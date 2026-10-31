package aisafe.aircrafts.domain;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RouteData(
        @JsonAlias({"origin", "originIataCode"}) String origin,
        @JsonAlias({"destination", "destinationIataCode"}) String destination,
        int estimatedFlightTime,
        double minimumRange,
        int minimumCapacity
) {}
