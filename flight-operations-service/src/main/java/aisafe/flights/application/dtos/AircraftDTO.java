package aisafe.flights.application.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AircraftDTO(
        String registrationNumber,
        String model,
        String status,
        Integer seatCapacity,
        Double range
) {
    public boolean isOutOfService() {
        return "UNDER_MAINTENANCE".equalsIgnoreCase(status) || "INACTIVE".equalsIgnoreCase(status);
    }
}
