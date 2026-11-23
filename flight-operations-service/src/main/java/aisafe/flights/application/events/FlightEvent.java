package aisafe.flights.application.events;

import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FlightEvent(
        String eventId,
        String eventType,
        Instant occurredAt,
        String flightId,
        long revision,
        String aircraftRegistration,
        String aircraftModel,
        String origin,
        String destination,
        OffsetDateTime departure,
        OffsetDateTime arrival,
        FlightStatus previousStatus,
        FlightStatus status
) {
    public static final String FLIGHT_SCHEDULED = "FLIGHT_SCHEDULED";
    public static final String FLIGHT_STATUS_CHANGED = "FLIGHT_STATUS_CHANGED";

    public static final String FLIGHT_SNAPSHOT = "FLIGHT_SNAPSHOT";

    public static FlightEvent scheduled(ScheduledFlight flight) {
        return of(FLIGHT_SCHEDULED, flight, null);
    }

    public static FlightEvent statusChanged(ScheduledFlight flight, FlightStatus previousStatus) {
        return of(FLIGHT_STATUS_CHANGED, flight, previousStatus);
    }

    public static FlightEvent snapshot(FlightResponse flight) {
        return new FlightEvent(UUID.randomUUID().toString(), FLIGHT_SNAPSHOT, Instant.now(),
                flight.id(), flight.revision(), flight.aircraftId(), flight.aircraftModel(),
                flight.originIataCode(), flight.destinationIataCode(),
                flight.departureDateTime(), flight.arrivalDateTime(), null, flight.status());
    }

    private static FlightEvent of(String type, ScheduledFlight flight, FlightStatus previousStatus) {
        return new FlightEvent(UUID.randomUUID().toString(), type, Instant.now(),
                flight.getId(), flight.getRevision(), flight.getAircraftRegistrationNumber(), flight.getAircraftModel(),
                flight.getOriginCode(), flight.getDestinationCode(),
                flight.getDepartureDateTime(), flight.getArrivalDateTime(), previousStatus, flight.getStatus());
    }

    public double durationHours() {
        return Duration.between(departure, arrival).toSeconds() / 3600.0;
    }

    public boolean completesTheFlight() {
        return status == FlightStatus.COMPLETED && previousStatus != FlightStatus.COMPLETED
                && FLIGHT_STATUS_CHANGED.equals(eventType);
    }
}
