package aisafe.aircrafts.cqrs.projections;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Read model projection entity for fleet status.
 */
@Entity
@Table(name = "aircraft_read_model")
@Getter
@Setter
@NoArgsConstructor
public class AircraftReadModel {

    @Id
    @Column(name = "registration_number", length = 32)
    private String registrationNumber;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "status")
    private String status;

    @Column(name = "seat_capacity")
    private Integer seatCapacity;

    @Column(name = "max_range")
    private Double range;

    @Column(name = "total_operational_hours")
    private Double totalOperationalHours = 0.0;

    @Column(name = "pending_maintenance_count")
    private Integer pendingMaintenanceCount = 0;

    @Column(name = "readiness_status")
    private String readinessStatus = "READY_FOR_FLIGHT";

    @Column(name = "operational_summary")
    private String operationalSummary = "Fully operational; ready for scheduled flights";

    @Column(name = "last_updated")
    private Instant lastUpdated = Instant.now();

    public AircraftReadModel(String registrationNumber, String modelName, String status, Integer seatCapacity, Double range) {
        this.registrationNumber = registrationNumber;
        this.modelName = modelName;
        this.status = status;
        this.seatCapacity = seatCapacity;
        this.range = range;
        recalculateReadinessStatus();
    }

    public AircraftReadModel(String registrationNumber, String modelName, String status, Double totalOperationalHours, Integer pendingMaintenanceCount) {
        this.registrationNumber = registrationNumber;
        this.modelName = modelName;
        this.status = status;
        this.totalOperationalHours = totalOperationalHours != null ? totalOperationalHours : 0.0;
        this.pendingMaintenanceCount = pendingMaintenanceCount != null ? pendingMaintenanceCount : 0;
        recalculateReadinessStatus();
    }

    public void recalculateReadinessStatus() {
        this.lastUpdated = Instant.now();
        if ("UNDER_MAINTENANCE".equalsIgnoreCase(this.status)) {
            this.readinessStatus = "UNDER_MAINTENANCE";
            this.operationalSummary = "Currently undergoing scheduled maintenance servicing in hangar";
        } else if ("INACTIVE".equalsIgnoreCase(this.status)) {
            this.readinessStatus = "GROUNDED";
            this.operationalSummary = "Aircraft grounded or decommissioned; awaiting clearance";
        } else if (this.pendingMaintenanceCount != null && this.pendingMaintenanceCount > 0) {
            this.readinessStatus = "MAINTENANCE_DUE";
            this.operationalSummary = "Active in fleet; scheduled maintenance inspection is pending";
        } else {
            this.readinessStatus = "READY_FOR_FLIGHT";
            this.operationalSummary = "Optimal operational conditions; ready for scheduled flights";
        }
    }
}
