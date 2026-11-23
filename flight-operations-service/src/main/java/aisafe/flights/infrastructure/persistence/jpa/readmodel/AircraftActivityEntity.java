package aisafe.flights.infrastructure.persistence.jpa.readmodel;

import aisafe.flights.domain.FlightStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "aircraft_activity_read_model")
@Getter
@Setter
public class AircraftActivityEntity {
    @Id
    private String registration;

    private String aircraftModel;

    private String aircraftStatus;

    private long totalFlights;
    private long completedFlights;
    private long canceledFlights;
    private double completedHours;

    @Enumerated(EnumType.STRING)
    private FlightStatus latestFlightStatus;

    private String mood;
    private String moodReason;
    private int sparkleLevel;

    private Instant updatedAt;

    protected AircraftActivityEntity() {
    }

    public AircraftActivityEntity(String registration) {
        this.registration = registration;
    }
}
