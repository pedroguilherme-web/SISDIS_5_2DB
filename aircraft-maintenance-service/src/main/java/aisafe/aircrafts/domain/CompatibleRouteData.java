package aisafe.aircrafts.domain;

public record CompatibleRouteData(
        String origin,
        String destination,
        int estimatedFlightTime,
        double minimumRange,
        int minimumCapacity
) {}
