package aisafe.shared.domain.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Event received from Flight Operations Service when a scheduled flight completes.
 * Demonstrates asynchronous inter-service event consumption across service boundaries.
 */
public class FlightCompletedEvent extends DomainEvent {

    private final String flightId;
    private final String aircraftRegistration;
    private final Double flightDurationHours;

    @JsonCreator
    public FlightCompletedEvent(
            @JsonProperty("flightId") String flightId,
            @JsonProperty("aircraftRegistration") String aircraftRegistration,
            @JsonProperty("flightDurationHours") Double flightDurationHours) {
        super("FLIGHT_COMPLETED");
        this.flightId = flightId;
        this.aircraftRegistration = aircraftRegistration;
        this.flightDurationHours = flightDurationHours;
    }

    public String getFlightId() {
        return flightId;
    }

    public String getAircraftRegistration() {
        return aircraftRegistration;
    }

    public Double getFlightDurationHours() {
        return flightDurationHours;
    }
}
