package aisafe.aircrafts.domain;

import java.time.OffsetDateTime;

public record FlightTimelineData(
        OffsetDateTime departureTime,
        OffsetDateTime arrivalTime,
        String origin,
        String destination
) {}
