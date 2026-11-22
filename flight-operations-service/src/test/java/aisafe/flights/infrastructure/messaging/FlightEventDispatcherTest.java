package aisafe.flights.infrastructure.messaging;

import aisafe.flights.application.events.FlightEvent;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.infrastructure.persistence.jpa.outbox.OutboxMessageEntity;
import aisafe.flights.infrastructure.persistence.jpa.outbox.OutboxMessageRepository;
import aisafe.flights.infrastructure.readmodel.FlightProjector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightEventDispatcherTest {
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 12, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    @Mock private FlightProjector projector;
    @Mock private OutboxMessageRepository outbox;
    @Mock private ObjectProvider<OutboxRelay> relayProvider;
    @Mock private OutboxRelay relay;

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private FlightEventDispatcher dispatcher(boolean messagingEnabled) {
        return new FlightEventDispatcher(projector, outbox, relayProvider, messagingEnabled);
    }

    private static FlightEvent completedEvent() {
        ScheduledFlight flight = new ScheduledFlight("f-1", T0, T0.plusMinutes(90), FlightStatus.IN_FLIGHT,
                "LIS", "OPO", "CS-TRP", "A320", 1);
        flight.changeStatus(FlightStatus.COMPLETED);
        return FlightEvent.statusChanged(flight, FlightStatus.IN_FLIGHT);
    }

    @Test
    void shouldWriteTheOutboxInsideTheTransactionAndDeliverOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        FlightEvent event = completedEvent();

        dispatcher(true).publish(event);

        verify(outbox, times(2)).save(any(OutboxMessageEntity.class));
        verifyNoInteractions(projector, relayProvider);

        when(relayProvider.getIfAvailable()).thenReturn(relay);
        TransactionSynchronizationUtils.triggerAfterCommit();
        verify(projector).apply(event);
        verify(relay).publishPending();
    }

    @Test
    void shouldStoreTheStatusChangeAndTheFlightCompletedContractInOrder() {
        dispatcher(true).publish(completedEvent());

        ArgumentCaptor<OutboxMessageEntity> rows = ArgumentCaptor.forClass(OutboxMessageEntity.class);
        verify(outbox, times(2)).save(rows.capture());
        List<OutboxMessageEntity> saved = rows.getAllValues();

        assertEquals(List.of("flight.status.changed", "flight.completed"), saved.stream().map(OutboxMessageEntity::getRoutingKey).toList());
        assertTrue(saved.get(0).getSequenceNumber() < saved.get(1).getSequenceNumber());
        assertEquals(MessagingTopology.BUS_EXCHANGE, saved.get(1).getExchangeName());
        assertEquals("{\"flightId\":\"f-1\",\"aircraftRegistration\":\"CS-TRP\",\"flightDurationHours\":1.5}",
                new String(saved.get(1).getPayload(), StandardCharsets.UTF_8),
                "exactly the fields of the Aircraft service's FlightCompletedEvent");
    }

    @Test
    void shouldStoreOnlyFlightScheduledForANewFlight() {
        ScheduledFlight flight = new ScheduledFlight(T0, T0.plusHours(1), FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TRP", "A320");

        dispatcher(true).publish(FlightEvent.scheduled(flight));

        ArgumentCaptor<OutboxMessageEntity> row = ArgumentCaptor.forClass(OutboxMessageEntity.class);
        verify(outbox).save(row.capture());
        assertEquals("flight.scheduled", row.getValue().getRoutingKey());
    }

    @Test
    void shouldOnlyProjectLocallyWhenMessagingIsDisabled() {
        dispatcher(false).publish(completedEvent());

        verify(projector).apply(any());
        verifyNoInteractions(outbox);
    }

    @Test
    void shouldNotFailTheCommandWhenTheImmediatePublicationFails() {
        when(relayProvider.getIfAvailable()).thenReturn(relay);
        when(relay.publishPending()).thenThrow(new IllegalStateException("broker down"));

        assertDoesNotThrow(() -> dispatcher(true).publish(completedEvent()));
        verify(projector).apply(any());
    }
}
