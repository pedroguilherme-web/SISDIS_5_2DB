package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.ports.FlightPeerClient;
import aisafe.flights.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChangeFlightStatusUseCaseTest {
    private static final OffsetDateTime DEPARTURE = OffsetDateTime.of(2026, 12, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    @Mock private ScheduledFlightRepository repository;
    @Mock private FlightPeerClient peers;
    @Mock private aisafe.flights.application.ports.FlightEventPublisher events;

    @InjectMocks
    private ChangeFlightStatusUseCase useCase;

    private static ScheduledFlight flight(FlightStatus status) {
        return new ScheduledFlight("f-1", DEPARTURE, DEPARTURE.plusHours(2), status, "LIS", "OPO", "CS-TRP", "A320");
    }

    @Test
    void shouldChangeStatusOfLocalFlight() {
        when(repository.findById("f-1")).thenReturn(Optional.of(flight(FlightStatus.IN_FLIGHT)));
        when(repository.save(any(ScheduledFlight.class))).thenAnswer(invocation -> invocation.getArgument(0));

        FlightResponse response = useCase.execute("f-1", FlightStatus.COMPLETED, QueryScope.CLUSTER);

        assertEquals(FlightStatus.COMPLETED, response.status());
        verifyNoInteractions(peers);
    }

    @Test
    void shouldRejectInvalidTransitionAndNotSave() {
        when(repository.findById("f-1")).thenReturn(Optional.of(flight(FlightStatus.SCHEDULED)));

        assertThrows(InvalidFlightStatusTransitionException.class,
                () -> useCase.execute("f-1", FlightStatus.COMPLETED, QueryScope.CLUSTER));
        verify(repository, never()).save(any());
    }

    @Test
    void shouldForwardToPeersWhenFlightIsStoredOnAnotherReplica() {
        FlightResponse peerAnswer = FlightResponse.from(flight(FlightStatus.COMPLETED));
        when(repository.findById("f-1")).thenReturn(Optional.empty());
        when(peers.changeStatus("f-1", FlightStatus.COMPLETED)).thenReturn(Optional.of(peerAnswer));

        assertEquals(peerAnswer, useCase.execute("f-1", FlightStatus.COMPLETED, QueryScope.CLUSTER));
    }

    @Test
    void shouldReturnNotFoundWhenNoReplicaHasTheFlight() {
        when(repository.findById("f-1")).thenReturn(Optional.empty());
        when(peers.changeStatus("f-1", FlightStatus.COMPLETED)).thenReturn(Optional.empty());

        assertThrows(FlightNotFoundException.class, () -> useCase.execute("f-1", FlightStatus.COMPLETED, QueryScope.CLUSTER));
    }

    @Test
    void shouldNotForwardAgainWhenRequestCameFromAPeer() {
        when(repository.findById("f-1")).thenReturn(Optional.empty());

        assertThrows(FlightNotFoundException.class, () -> useCase.execute("f-1", FlightStatus.COMPLETED, QueryScope.LOCAL));
        verifyNoInteractions(peers);
    }

    @Test
    void shouldPublishStatusChangeWithPreviousStatusAndNewRevision() {
        when(repository.findById("f-1")).thenReturn(Optional.of(flight(FlightStatus.IN_FLIGHT)));
        when(repository.save(any(ScheduledFlight.class))).thenAnswer(invocation -> invocation.getArgument(0));

        useCase.execute("f-1", FlightStatus.COMPLETED, QueryScope.CLUSTER);

        org.mockito.ArgumentCaptor<aisafe.flights.application.events.FlightEvent> event =
                org.mockito.ArgumentCaptor.forClass(aisafe.flights.application.events.FlightEvent.class);
        verify(events).publish(event.capture());
        assertEquals(aisafe.flights.application.events.FlightEvent.FLIGHT_STATUS_CHANGED, event.getValue().eventType());
        assertEquals(FlightStatus.IN_FLIGHT, event.getValue().previousStatus());
        assertEquals(FlightStatus.COMPLETED, event.getValue().status());
        assertEquals(1, event.getValue().revision());
        assertTrue(event.getValue().completesTheFlight());
    }

    @Test
    void shouldNotPublishWhenTheTransitionIsInvalidOrTheFlightIsOnAPeer() {
        when(repository.findById("f-1")).thenReturn(Optional.of(flight(FlightStatus.SCHEDULED)));
        assertThrows(InvalidFlightStatusTransitionException.class,
                () -> useCase.execute("f-1", FlightStatus.COMPLETED, QueryScope.CLUSTER));

        when(repository.findById("f-2")).thenReturn(Optional.empty());
        when(peers.changeStatus("f-2", FlightStatus.CANCELED)).thenReturn(Optional.of(FlightResponse.from(flight(FlightStatus.CANCELED))));
        useCase.execute("f-2", FlightStatus.CANCELED, QueryScope.CLUSTER);

        verifyNoInteractions(events);
    }
}
