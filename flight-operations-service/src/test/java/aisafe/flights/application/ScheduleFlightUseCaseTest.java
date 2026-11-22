package aisafe.flights.application;

import aisafe.flights.application.dtos.AircraftDTO;
import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.dtos.RouteDTO;
import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.flights.application.ports.AircraftServiceClient;
import aisafe.flights.application.ports.FlightPeerClient;
import aisafe.flights.application.ports.RouteServiceClient;
import aisafe.flights.domain.*;
import aisafe.shared.domain.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleFlightUseCaseTest {
    private static final OffsetDateTime DEPARTURE = OffsetDateTime.of(2026, 12, 1, 8, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime ARRIVAL = DEPARTURE.plusHours(2);

    @Mock private ScheduledFlightRepository repository;
    @Mock private AircraftServiceClient aircraftService;
    @Mock private RouteServiceClient routeService;
    @Mock private FlightPeerClient peers;
    @Mock private aisafe.flights.application.ports.FlightEventPublisher events;
    @Mock private aisafe.flights.application.ports.FlightOwnership ownership;

    @InjectMocks
    private ScheduleFlightUseCase useCase;

    private final ScheduleFlightRequest request = new ScheduleFlightRequest(" cs-trp ", "lis", "opo", DEPARTURE, ARRIVAL);

    @BeforeEach
    void setUp() {
        lenient().when(repository.save(any(ScheduledFlight.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static RouteDTO route(Integer minimumCapacity, Double minimumRange, String status) {
        return new RouteDTO("LIS", "OPO", minimumRange, minimumCapacity, status);
    }

    private static AircraftDTO aircraft(String status, Integer seats, Double range) {
        return new AircraftDTO("CS-TRP", "A320", status, seats, range);
    }

    private void givenValidRouteAndAircraft() {
        when(routeService.findRoute("LIS", "OPO")).thenReturn(Optional.of(route(100, 500.0, "ACTIVE")));
        when(aircraftService.findByRegistration("CS-TRP")).thenReturn(Optional.of(aircraft("AVAILABLE", 150, 3000.0)));
    }

    @Test
    void shouldScheduleFlightWithNormalizedCodesAndAircraftModel() {
        givenValidRouteAndAircraft();

        FlightResponse response = useCase.execute(request);

        ArgumentCaptor<ScheduledFlight> saved = ArgumentCaptor.forClass(ScheduledFlight.class);
        verify(repository).save(saved.capture());
        assertEquals("CS-TRP", saved.getValue().getAircraftRegistrationNumber());
        assertEquals("LIS", saved.getValue().getOriginCode());
        assertEquals("OPO", saved.getValue().getDestinationCode());
        assertEquals("A320", saved.getValue().getAircraftModel());
        assertEquals(FlightStatus.SCHEDULED, response.status());
        assertNotNull(response.id());
    }

    @Test
    void shouldCheckScheduleConflictsOnPeerReplicas() {
        givenValidRouteAndAircraft();

        useCase.execute(request);

        verify(repository).existsByOverlappingSchedule("CS-TRP", DEPARTURE, ARRIVAL);
        verify(peers).anyPeerHasOverlappingFlight("CS-TRP", DEPARTURE, ARRIVAL);
    }

    @Test
    void shouldRejectWhenAnotherReplicaAlreadyHasAnOverlappingFlight() {
        givenValidRouteAndAircraft();
        when(peers.anyPeerHasOverlappingFlight("CS-TRP", DEPARTURE, ARRIVAL)).thenReturn(true);

        assertThrows(AircraftUnavailableException.class, () -> useCase.execute(request));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectLocalConflictWithoutAskingPeers() {
        givenValidRouteAndAircraft();
        when(repository.existsByOverlappingSchedule("CS-TRP", DEPARTURE, ARRIVAL)).thenReturn(true);

        assertThrows(AircraftUnavailableException.class, () -> useCase.execute(request));
        verify(peers, never()).anyPeerHasOverlappingFlight(anyString(), any(), any());
    }

    @Test
    void shouldRejectUnknownRoute() {
        when(routeService.findRoute("LIS", "OPO")).thenReturn(Optional.empty());

        ResourceNotFoundException ex = assertThrows(ResourceNotFoundException.class, () -> useCase.execute(request));
        assertEquals("Route not found: LIS -> OPO", ex.getMessage());
        verifyNoInteractions(aircraftService);
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectInactiveRoute() {
        when(routeService.findRoute("LIS", "OPO")).thenReturn(Optional.of(route(100, 500.0, "SUSPENDED")));

        assertThrows(InvalidFlightScheduleException.class, () -> useCase.execute(request));
        verifyNoInteractions(aircraftService);
    }

    @Test
    void shouldRejectUnknownAircraft() {
        when(routeService.findRoute("LIS", "OPO")).thenReturn(Optional.of(route(100, 500.0, "ACTIVE")));
        when(aircraftService.findByRegistration("CS-TRP")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> useCase.execute(request));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNDER_MAINTENANCE", "INACTIVE"})
    void shouldRejectAircraftOutOfService(String status) {
        when(routeService.findRoute("LIS", "OPO")).thenReturn(Optional.of(route(100, 500.0, "ACTIVE")));
        when(aircraftService.findByRegistration("CS-TRP")).thenReturn(Optional.of(aircraft(status, 150, 3000.0)));

        AircraftUnavailableException ex = assertThrows(AircraftUnavailableException.class, () -> useCase.execute(request));
        assertTrue(ex.getMessage().contains(status));
    }

    @Test
    void shouldRejectAircraftWithTooFewSeats() {
        when(routeService.findRoute("LIS", "OPO")).thenReturn(Optional.of(route(200, 500.0, "ACTIVE")));
        when(aircraftService.findByRegistration("CS-TRP")).thenReturn(Optional.of(aircraft("AVAILABLE", 150, 3000.0)));

        assertThrows(AircraftIncompatibilityException.class, () -> useCase.execute(request));
    }

    @Test
    void shouldRejectAircraftWithInsufficientRange() {
        when(routeService.findRoute("LIS", "OPO")).thenReturn(Optional.of(route(100, 5000.0, "ACTIVE")));
        when(aircraftService.findByRegistration("CS-TRP")).thenReturn(Optional.of(aircraft("AVAILABLE", 150, 3000.0)));

        assertThrows(AircraftIncompatibilityException.class, () -> useCase.execute(request));
    }

    @Test
    void shouldAcceptRouteWithoutMinimumRequirements() {
        when(routeService.findRoute("LIS", "OPO")).thenReturn(Optional.of(route(null, null, null)));
        when(aircraftService.findByRegistration("CS-TRP")).thenReturn(Optional.of(aircraft("AVAILABLE", null, null)));

        assertEquals(FlightStatus.SCHEDULED, useCase.execute(request).status());
    }

    @Test
    void shouldRejectInvertedDatesBeforeCallingOtherServices() {
        ScheduleFlightRequest inverted = new ScheduleFlightRequest("CS-TRP", "LIS", "OPO", ARRIVAL, DEPARTURE);

        assertThrows(InvalidFlightScheduleException.class, () -> useCase.execute(inverted));
        verifyNoInteractions(routeService, aircraftService, peers);
    }

    @Test
    void shouldPropagateUnavailabilityOfRoutesServiceAndSaveNothing() {
        when(routeService.findRoute("LIS", "OPO")).thenThrow(new ExternalServiceUnavailableException("Airports & Routes service"));

        assertThrows(ExternalServiceUnavailableException.class, () -> useCase.execute(request));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldCreateFlightWithInitialStatusGivenByImport() {
        givenValidRouteAndAircraft();

        FlightResponse response = useCase.execute(request, FlightStatus.COMPLETED);

        assertEquals(FlightStatus.COMPLETED, response.status());
    }

    @Test
    void shouldNotCheckConflictsForACanceledFlight() {
        givenValidRouteAndAircraft();

        useCase.execute(request, FlightStatus.CANCELED);

        verify(repository, never()).existsByOverlappingSchedule(anyString(), any(), any());
        verifyNoInteractions(peers);
    }

    @Test
    void shouldPublishAFlightScheduledEventWithTheSavedState() {
        givenValidRouteAndAircraft();

        FlightResponse response = useCase.execute(request);

        ArgumentCaptor<aisafe.flights.application.events.FlightEvent> event =
                ArgumentCaptor.forClass(aisafe.flights.application.events.FlightEvent.class);
        verify(events).publish(event.capture());
        assertEquals(aisafe.flights.application.events.FlightEvent.FLIGHT_SCHEDULED, event.getValue().eventType());
        assertEquals(response.id(), event.getValue().flightId());
        assertEquals(0, event.getValue().revision());
        assertEquals("A320", event.getValue().aircraftModel());
    }

    @Test
    void shouldNotPublishAnythingWhenTheCommandIsRejected() {
        when(routeService.findRoute("LIS", "OPO")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> useCase.execute(request));
        verifyNoInteractions(events);
    }

    @Test
    void shouldForwardTheCommandToTheReplicaThatOwnsTheAircraft() {
        FlightResponse ownerAnswer = new FlightResponse("f-9", "CS-TRP", "A320", "LIS", "OPO", DEPARTURE, ARRIVAL,
                FlightStatus.SCHEDULED, 0);
        when(ownership.ownerOf("CS-TRP")).thenReturn(Optional.of("http://localhost:8072"));
        when(peers.scheduleOn("http://localhost:8072", request, FlightStatus.SCHEDULED)).thenReturn(ownerAnswer);

        assertEquals(ownerAnswer, useCase.execute(request));
        verifyNoInteractions(routeService, aircraftService, events);
        verify(repository, never()).save(any());
        verify(repository, never()).lockAircraftForScheduling(anyString());
    }

    @Test
    void shouldExecuteLocallyAForwardedCommandWithoutForwardingAgain() {
        givenValidRouteAndAircraft();

        useCase.execute(request, FlightStatus.SCHEDULED, QueryScope.LOCAL);

        verifyNoInteractions(ownership);
        verify(repository).save(any(ScheduledFlight.class));
    }

    @Test
    void shouldLockTheAircraftBeforeCheckingForConflicts() {
        givenValidRouteAndAircraft();

        useCase.execute(request);

        var order = inOrder(repository);
        order.verify(repository).lockAircraftForScheduling("CS-TRP");
        order.verify(repository).existsByOverlappingSchedule("CS-TRP", DEPARTURE, ARRIVAL);
        order.verify(repository).save(any(ScheduledFlight.class));
    }
}
