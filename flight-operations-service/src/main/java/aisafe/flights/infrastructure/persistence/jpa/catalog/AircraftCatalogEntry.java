package aisafe.flights.infrastructure.persistence.jpa.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "aircraft_catalog")
@Getter
@Setter
public class AircraftCatalogEntry {
    @Id
    private String registration;
    private String model;
    private String status;
    private Integer seatCapacity;
    private Double rangeKm;

    private String source;
    private Instant refreshedAt;

    protected AircraftCatalogEntry() {
    }

    public AircraftCatalogEntry(String registration) {
        this.registration = registration;
    }
}
