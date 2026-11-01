package aisafe.flights.infrastructure.persistence.jpa;

import aisafe.flights.domain.FlightStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "scheduled_flight")
@Getter
@Setter
public class ScheduledFlightJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private OffsetDateTime departureDateTime;

    @Column(nullable = false)
    private OffsetDateTime arrivalDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlightStatus status;

    // Em vez de RouteJpaEntity, guardamos apenas os códigos (Strings)
    @Column(nullable = false)
    private String originCode;

    @Column(nullable = false)
    private String destinationCode;

    // Em vez de AircraftJpaEntity, guardamos apenas a matrícula (String)
    @Column(nullable = false)
    private String aircraftRegistrationNumber;

    @Version
    private Long version;

    public ScheduledFlightJpaEntity() {}

    public ScheduledFlightJpaEntity(OffsetDateTime departureDateTime, OffsetDateTime arrivalDateTime,
                                    FlightStatus status, String originCode, String destinationCode, String aircraftRegistrationNumber) {
        this.departureDateTime = departureDateTime;
        this.arrivalDateTime = arrivalDateTime;
        this.status = status;
        this.originCode = originCode;
        this.destinationCode = destinationCode;
        this.aircraftRegistrationNumber = aircraftRegistrationNumber;
    }
}