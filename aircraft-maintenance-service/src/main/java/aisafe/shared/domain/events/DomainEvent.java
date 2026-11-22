package aisafe.shared.domain.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Base domain event representing an immutable state change in the system.
 * Part of Event-Driven Architecture and CQRS for LETI-SIDIS Assignment 2.
 */
public abstract class DomainEvent {

    private final String eventId;
    private final Instant timestamp;
    private final String eventType;

    protected DomainEvent(String eventType) {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = Instant.now();
        this.eventType = eventType;
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getEventType() {
        return eventType;
    }
}
