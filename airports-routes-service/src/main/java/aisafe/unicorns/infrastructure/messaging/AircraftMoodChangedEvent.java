package aisafe.unicorns.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AircraftMoodChangedEvent(
        String eventId,
        String correlationId,
        String registration,
        @JsonAlias("mood") String moodStatus,
        @JsonAlias("occurredAt") Instant timestamp
) {}
