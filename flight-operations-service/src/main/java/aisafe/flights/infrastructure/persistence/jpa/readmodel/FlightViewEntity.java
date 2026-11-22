package aisafe.flights.infrastructure.persistence.jpa.readmodel;

import aisafe.flights.application.events.FlightEvent;
import aisafe.flights.domain.FlightStatus;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
@Table(name = "flight_read_model", indexes = {
        @Index(name = "idx_flight_view_aircraft", columnList = "aircraftRegistration"),
        @Index(name = "idx_flight_view_route", columnList = "origin, destination"),
        @Index(name = "idx_flight_view_status", columnList = "status")
})
@Getter
public class FlightViewEntity {
    @Id
    @Column(length = 36)
    private String flightId;

    private String aircraftRegistration;
    private String aircraftModel;
    private String origin;
    private String destination;
    private OffsetDateTime departure;
    private OffsetDateTime arrival;

    @Enumerated(EnumType.STRING)
    private FlightStatus status;

    private double durationHours;

    private long revision;

    private String lastEventId;
    private Instant updatedAt;

    private long projectionSequence;

    protected FlightViewEntity() {
    }

    public FlightViewEntity(String flightId) {
        this.flightId = flightId;
        this.revision = -1;
    }

    public boolean isOlderThan(FlightEvent event) {
        return event.revision() > revision;
    }

    public void apply(FlightEvent event, long sequence) {
        this.projectionSequence = sequence;
        this.aircraftRegistration = event.aircraftRegistration();
        this.aircraftModel = event.aircraftModel();
        this.origin = event.origin();
        this.destination = event.destination();
        this.departure = event.departure();
        this.arrival = event.arrival();
        this.status = event.status();
        this.durationHours = event.durationHours();
        this.revision = event.revision();
        this.lastEventId = event.eventId();
        this.updatedAt = Instant.now();
    }
}
