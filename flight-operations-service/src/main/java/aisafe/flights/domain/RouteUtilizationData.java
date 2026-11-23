package aisafe.flights.domain;

public record RouteUtilizationData(
        String origin,
        String destination,
        long count
) {}
