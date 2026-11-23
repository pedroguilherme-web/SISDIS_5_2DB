package aisafe.flights.application;

import aisafe.flights.application.dtos.AircraftDTO;
import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.ports.AircraftServiceClient;
import aisafe.flights.application.ports.FlightPeerClient;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.domain.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewScheduledFlightsByAircraftUseCaseTest {
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 12, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    @Mock private ScheduledFlightRepository repository;
    @Mock private AircraftServiceClient aircraftService;
    @Mock private FlightPeerClient peers;

    @InjectMocks
    private ViewScheduledFlightsByAircraftUseCase useCase;

    private static ScheduledFlight localFlight(String id, OffsetDateTime departure) {
        return new ScheduledFlight(id, departure, departure.plusHours(1), FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TRP", "A320");
    }

    private void givenAircraftExists() {
        when(aircraftService.findByRegistration("CS-TRP"))
                .thenReturn(Optional.of(new AircraftDTO("CS-TRP", "A320", "AVAILABLE", 150, 3000.0)));
    }

    @Test
    void shouldMergeLocalAndPeerFlightsSortedByDeparture() {
        givenAircraftExists();
        when(repository.findByAircraftRegistration("CS-TRP")).thenReturn(List.of(localFlight("local-2", T0.plusDays(2))));
        when(peers.findFlightsByAircraft("CS-TRP"))
                .thenReturn(List.of(FlightResponse.from(localFlight("peer-1", T0))));

        List<FlightResponse> flights = useCase.execute(" cs-trp ", QueryScope.CLUSTER);

        assertEquals(List.of("peer-1", "local-2"), flights.stream().map(FlightResponse::id).toList());
    }

    @Test
    void shouldReturnNotFoundWhenAircraftDoesNotExist() {
        when(aircraftService.findByRegistration("CS-XXX")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> useCase.execute("CS-XXX", QueryScope.CLUSTER));
        verifyNoInteractions(peers);
    }

    @Test
    void shouldStillListFlightsWhenAircraftServiceIsDown() {
        when(aircraftService.findByRegistration("CS-TRP"))
                .thenThrow(new ExternalServiceUnavailableException("Aircraft & Maintenance service"));
        when(repository.findByAircraftRegistration("CS-TRP")).thenReturn(List.of(localFlight("local-1", T0)));
        when(peers.findFlightsByAircraft("CS-TRP")).thenReturn(List.of());

        assertEquals(1, useCase.execute("CS-TRP", QueryScope.CLUSTER).size());
    }

    @Test
    void shouldAnswerOnlyWithLocalDataWhenRequestCameFromAPeer() {
        when(repository.findByAircraftRegistration("CS-TRP")).thenReturn(List.of(localFlight("local-1", T0)));

        List<FlightResponse> flights = useCase.execute("CS-TRP", QueryScope.LOCAL);

        assertEquals(1, flights.size());
        verifyNoInteractions(peers, aircraftService);
    }
}
