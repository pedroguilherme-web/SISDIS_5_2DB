package aisafe.flights.infrastructure.persistence.jpa;

import aisafe.flights.domain.FlightStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "scheduled_flight", indexes = {
        @Index(name = "idx_flight_aircraft", columnList = "aircraftRegistrationNumber"),
        @Index(name = "idx_flight_route", columnList = "originCode, destinationCode")
})
@Getter
@Setter
public class ScheduledFlightJpaEntity {
    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private OffsetDateTime departureDateTime;

    @Column(nullable = false)
    private OffsetDateTime arrivalDateTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FlightStatus status;

    @Column(nullable = false)
    private String originCode;

    @Column(nullable = false)
    private String destinationCode;

    @Column(nullable = false)
    private String aircraftRegistrationNumber;

    @Column
    private String aircraftModel;

    @Column(nullable = false)
    private long revision;

    @Version
    private Long version;

    public ScheduledFlightJpaEntity() {}

    public ScheduledFlightJpaEntity(String id, OffsetDateTime departureDateTime, OffsetDateTime arrivalDateTime,
                                    FlightStatus status, String originCode, String destinationCode,
                                    String aircraftRegistrationNumber, String aircraftModel) {
        this.id = id;
        this.departureDateTime = departureDateTime;
        this.arrivalDateTime = arrivalDateTime;
        this.status = status;
        this.originCode = originCode;
        this.destinationCode = destinationCode;
        this.aircraftRegistrationNumber = aircraftRegistrationNumber;
        this.aircraftModel = aircraftModel;
    }
}
