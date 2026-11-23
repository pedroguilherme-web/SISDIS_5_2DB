package aisafe.flights.application.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RouteDTO(
        String originIataCode,
        String destinationIataCode,
        Double minimumRange,
        Integer minimumCapacity,
        String status
) {
    public boolean isActive() {
        return status == null || "ACTIVE".equalsIgnoreCase(status);
    }
}
