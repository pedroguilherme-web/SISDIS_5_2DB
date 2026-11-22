package aisafe.flights.application.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FlightUtilizationResponse(
        String origin,
        String destination,
        long count
) {}
