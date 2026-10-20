package aisafe.aircrafts.domain;

public record RouteData(
        String origin,
        String destination,
        int estimatedFlightTime,
        double minimumRange,
        int minimumCapacity
) {}
