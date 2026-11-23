package aisafe.flights.infrastructure.persistence.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "aircraft_schedule_lock")
public class AircraftScheduleLockEntity {
    @Id
    private String registration;

    protected AircraftScheduleLockEntity() {
    }

    public AircraftScheduleLockEntity(String registration) {
        this.registration = registration;
    }

    public String getRegistration() {
        return registration;
    }
}
