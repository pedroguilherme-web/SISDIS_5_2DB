package aisafe.airports.infrastructure.messaging;

import java.time.Instant;

public record AirportChangedEvent(
        String eventId,
        String correlationId,
        String eventType,
        String iataCode,
        String name,
        String city,
        String country,
        String region,
        String timezone,
        String status,
        Instant occurredAt
) {
}
