package aisafe.flights.application.dtos;

import aisafe.flights.domain.ScheduledFlight;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FlightTimelineResponse(
        OffsetDateTime departureTime,
        OffsetDateTime arrivalTime,
        String origin,
        String destination
) {
    public static FlightTimelineResponse from(ScheduledFlight flight) {
        return new FlightTimelineResponse(flight.getDepartureDateTime(), flight.getArrivalDateTime(),
                flight.getOriginCode(), flight.getDestinationCode());
    }
}
