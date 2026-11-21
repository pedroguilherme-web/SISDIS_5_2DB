package aisafe.routes.infrastructure.messaging;

import java.time.Instant;

public record RouteChangedEvent(
        String eventId,
        String correlationId,
        String eventType,
        String originIataCode,
        String destinationIataCode,
        Integer estimatedFlightTime,
        Double minimumRange,
        Integer minimumCapacity,
        String status,
        Instant occurredAt
) {
}
