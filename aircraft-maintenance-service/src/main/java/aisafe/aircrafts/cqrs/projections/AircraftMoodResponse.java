package aisafe.aircrafts.cqrs.projections;

import java.time.Instant;

/**
 * DTO representing the airplane mood status for the mandatory /api/v2/unicorns endpoint.
 * Demonstrates a specialized CQRS Read Model projection.
 */
public record AircraftMoodResponse(
        String registrationNumber,
        String model,
        String operationalStatus,
        String mood,
        Integer sparkleLevel,
        String moodReason,
        Instant lastUpdated
) {
    public static AircraftMoodResponse fromReadModel(AircraftReadModel model) {
        return new AircraftMoodResponse(
                model.getRegistrationNumber(),
                model.getModelName(),
                model.getStatus(),
                model.getMoodStatus(),
                model.getSparkleLevel(),
                model.getMoodReason(),
                model.getLastUpdated()
        );
    }
}
