package aisafe.flights.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

final class InboundMessages {
    private InboundMessages() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AircraftMessage(
            String registrationNumber,
            String modelName,
            String status,
            String newStatus,
            Integer seatCapacity,
            Double range
    ) {
        String effectiveStatus() {
            return newStatus != null ? newStatus : status;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RouteMessage(
            String eventType,
            @JsonAlias("origin") String originIataCode,
            @JsonAlias("destination") String destinationIataCode,
            Double minimumRange,
            Integer minimumCapacity,
            String status
    ) {
        boolean isDeletion() {
            return "RouteDeleted".equalsIgnoreCase(eventType);
        }
    }

    record FlightCompletedMessage(String flightId, String aircraftRegistration, Double flightDurationHours) {
    }
}
