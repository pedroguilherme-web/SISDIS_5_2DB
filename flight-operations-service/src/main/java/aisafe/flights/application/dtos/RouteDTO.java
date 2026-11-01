package aisafe.flights.application.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RouteDTO(
        int minimumCapacity,
        int minimumRange
) {}