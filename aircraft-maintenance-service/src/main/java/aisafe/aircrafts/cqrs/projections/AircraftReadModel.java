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
 * CQRS Read Model for Aircraft and Fleet status.
 * Denormalized entity stored in the read table 'aircraft_read_model'.
 * Optimized specifically for fast read operations, dashboards, and mood projections.
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

    @Column(name = "mood_status")
    private String moodStatus = "HAPPY";

    @Column(name = "sparkle_level")
    private Integer sparkleLevel = 90;

    @Column(name = "mood_reason")
    private String moodReason = "Fully operational and ready for flight";

    @Column(name = "last_updated")
    private Instant lastUpdated = Instant.now();

    public AircraftReadModel(String registrationNumber, String modelName, String status, Integer seatCapacity, Double range) {
        this.registrationNumber = registrationNumber;
        this.modelName = modelName;
        this.status = status;
        this.seatCapacity = seatCapacity;
        this.range = range;
        recalculateMood();
    }

    public AircraftReadModel(String registrationNumber, String modelName, String status, Double totalOperationalHours, Integer pendingMaintenanceCount) {
        this.registrationNumber = registrationNumber;
        this.modelName = modelName;
        this.status = status;
        this.totalOperationalHours = totalOperationalHours != null ? totalOperationalHours : 0.0;
        this.pendingMaintenanceCount = pendingMaintenanceCount != null ? pendingMaintenanceCount : 0;
        recalculateMood();
    }

    public void recalculateMood() {
        this.lastUpdated = Instant.now();
        if ("UNDER_MAINTENANCE".equalsIgnoreCase(this.status)) {
            this.moodStatus = "UNDER_CARE";
            this.sparkleLevel = 45;
            this.moodReason = "Currently undergoing maintenance servicing in the hangar";
        } else if ("INACTIVE".equalsIgnoreCase(this.status)) {
            this.moodStatus = "GRUMPY";
            this.sparkleLevel = 15;
            this.moodReason = "Grounded or inactive; awaiting operational clearance";
        } else if (this.pendingMaintenanceCount != null && this.pendingMaintenanceCount > 0) {
            this.moodStatus = "TIRED";
            this.sparkleLevel = 60;
            this.moodReason = "Operating smoothly, but maintenance inspection is due soon";
        } else if (this.totalOperationalHours != null && this.totalOperationalHours > 1000.0) {
            this.moodStatus = "PROUD";
            this.sparkleLevel = 88;
            this.moodReason = "Veteran aircraft with over 1000 hours of reliable flight";
        } else {
            this.moodStatus = "HAPPY";
            this.sparkleLevel = 98;
            this.moodReason = "Optimal flight conditions; eager for the next destination!";
        }
    }
}
