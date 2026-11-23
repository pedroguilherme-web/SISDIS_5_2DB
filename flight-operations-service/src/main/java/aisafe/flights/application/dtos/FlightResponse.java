package aisafe.flights.application.dtos;

import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FlightResponse(
        String id,
        String aircraftId,
        String aircraftModel,
        String originIataCode,
        String destinationIataCode,
        OffsetDateTime departureDateTime,
        OffsetDateTime arrivalDateTime,
        FlightStatus status,
        long revision
) {
    public static FlightResponse from(ScheduledFlight flight) {
        return new FlightResponse(
                flight.getId(),
                flight.getAircraftRegistrationNumber(),
                flight.getAircraftModel(),
                flight.getOriginCode(),
                flight.getDestinationCode(),
                flight.getDepartureDateTime(),
                flight.getArrivalDateTime(),
                flight.getStatus(),
                flight.getRevision()
        );
    }
}
