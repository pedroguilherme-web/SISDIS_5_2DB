package aisafe.aircrafts.cqrs.projections;

import java.time.Instant;

/**
 * DTO representing the aircraft fleet status for read queries.
 */
public record AircraftFleetSummaryResponse(
        String registrationNumber,
        String model,
        String operationalStatus,
        String readinessStatus,
        Integer pendingMaintenanceCount,
        Double totalOperationalHours,
        String operationalSummary,
        Instant lastUpdated
) {
    public static AircraftFleetSummaryResponse fromReadModel(AircraftReadModel model) {
        return new AircraftFleetSummaryResponse(
                model.getRegistrationNumber(),
                model.getModelName(),
                model.getStatus(),
                model.getReadinessStatus(),
                model.getPendingMaintenanceCount(),
                model.getTotalOperationalHours(),
                model.getOperationalSummary(),
                model.getLastUpdated()
        );
    }
}
