package aisafe.flights.application.dtos;

public record AircraftDTO(
        String status,
        int seatCapacity,
        int range
) {}