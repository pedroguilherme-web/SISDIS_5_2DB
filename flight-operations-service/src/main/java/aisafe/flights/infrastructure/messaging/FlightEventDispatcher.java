package aisafe.flights.infrastructure.messaging;

import aisafe.flights.application.events.FlightEvent;
import aisafe.flights.application.ports.FlightEventPublisher;
import aisafe.flights.infrastructure.messaging.InboundMessages.FlightCompletedMessage;
import aisafe.flights.infrastructure.persistence.jpa.outbox.OutboxMessageEntity;
import aisafe.flights.infrastructure.persistence.jpa.outbox.OutboxMessageRepository;
import aisafe.flights.infrastructure.readmodel.FlightProjector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.atomic.AtomicLong;

import static aisafe.flights.infrastructure.messaging.MessagingTopology.*;

@Slf4j
@Component
public class FlightEventDispatcher implements FlightEventPublisher {
    private final FlightProjector projector;
    private final OutboxMessageRepository outbox;
    private final ObjectProvider<OutboxRelay> relay;
    private final boolean messagingEnabled;
    private final AtomicLong sequence = new AtomicLong(-1);

    public FlightEventDispatcher(FlightProjector projector,
                                 OutboxMessageRepository outbox,
                                 ObjectProvider<OutboxRelay> relay,
                                 @Value("${aisafe.messaging.enabled:false}") boolean messagingEnabled) {
        this.projector = projector;
        this.outbox = outbox;
        this.relay = relay;
        this.messagingEnabled = messagingEnabled;
    }

    @Override
    public void publish(FlightEvent event) {
        // Transactional outbox: the messages are stored in the command's transaction, so they are never lost or sent for a rollback.
        if (messagingEnabled) {
            storeInOutbox(event);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    afterCommitted(event);
                }
            });
        } else {
            afterCommitted(event);
        }
    }

    private void storeInOutbox(FlightEvent event) {
        String routingKey = FlightEvent.FLIGHT_SCHEDULED.equals(event.eventType()) ? FLIGHT_SCHEDULED : FLIGHT_STATUS_CHANGED;
        outbox.save(new OutboxMessageEntity(event.eventId(), nextSequence(), BUS_EXCHANGE, routingKey,
                event.eventType(), EventJson.write(event)));

        if (event.completesTheFlight()) {
            FlightCompletedMessage completed = new FlightCompletedMessage(
                    event.flightId(), event.aircraftRegistration(), event.durationHours());
            outbox.save(new OutboxMessageEntity(event.eventId() + "-completed", nextSequence(), BUS_EXCHANGE,
                    FLIGHT_COMPLETED, "FLIGHT_COMPLETED", EventJson.write(completed)));
        }
    }

    void afterCommitted(FlightEvent event) {
        try {
            projector.apply(event);
        } catch (RuntimeException e) {
            log.warn("Local projection of {} for flight {} failed: {}", event.eventType(), event.flightId(), e.getMessage());
        }
        OutboxRelay outboxRelay = relay.getIfAvailable();
        if (outboxRelay != null) {
            try {
                outboxRelay.publishPending();
            } catch (RuntimeException e) {
                log.warn("Outbox relay failed; the scheduled relay will retry: {}", e.getMessage());
            }
        }
    }

    private long nextSequence() {
        sequence.compareAndSet(-1, outbox.maxSequenceNumber());
        return sequence.incrementAndGet();
    }
}
