package aisafe.flights.domain;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

public class ScheduledFlight {
    private final String id;
    private final OffsetDateTime departureDateTime;
    private final OffsetDateTime arrivalDateTime;
    private FlightStatus status;
    private final String originCode;
    private final String destinationCode;
    private final String aircraftRegistrationNumber;
    private final String aircraftModel;

    private long revision;

    public ScheduledFlight(OffsetDateTime departureDateTime, OffsetDateTime arrivalDateTime,
                           FlightStatus status, String originCode, String destinationCode,
                           String aircraftRegistrationNumber, String aircraftModel) {
        // UUID instead of a database sequence: every replica has its own database, so ids must be unique cluster-wide.
        this(UUID.randomUUID().toString(), departureDateTime, arrivalDateTime, status,
                originCode, destinationCode, aircraftRegistrationNumber, aircraftModel);
    }

    public ScheduledFlight(String id, OffsetDateTime departureDateTime, OffsetDateTime arrivalDateTime,
                           FlightStatus status, String originCode, String destinationCode,
                           String aircraftRegistrationNumber, String aircraftModel) {
        this(id, departureDateTime, arrivalDateTime, status, originCode, destinationCode,
                aircraftRegistrationNumber, aircraftModel, 0);
    }

    public ScheduledFlight(String id, OffsetDateTime departureDateTime, OffsetDateTime arrivalDateTime,
                           FlightStatus status, String originCode, String destinationCode,
                           String aircraftRegistrationNumber, String aircraftModel, long revision) {
        if (id == null || id.isBlank()) throw new InvalidFlightScheduleException("Flight id must not be null.");
        if (departureDateTime == null) throw new InvalidFlightScheduleException("Departure date/time must not be null.");
        if (arrivalDateTime == null) throw new InvalidFlightScheduleException("Arrival date/time must not be null.");
        if (status == null) throw new InvalidFlightScheduleException("Flight status must not be null.");
        if (originCode == null || originCode.isBlank()) throw new InvalidFlightScheduleException("Origin code must not be null.");
        if (destinationCode == null || destinationCode.isBlank()) throw new InvalidFlightScheduleException("Destination code must not be null.");
        if (originCode.equalsIgnoreCase(destinationCode)) throw new InvalidFlightScheduleException("Origin and destination must be different.");
        if (aircraftRegistrationNumber == null || aircraftRegistrationNumber.isBlank()) throw new InvalidFlightScheduleException("Aircraft registration must not be null.");
        if (!departureDateTime.isBefore(arrivalDateTime)) throw new InvalidFlightScheduleException("Departure must be before arrival.");

        this.id = id;
        this.departureDateTime = departureDateTime;
        this.arrivalDateTime = arrivalDateTime;
        this.status = status;
        this.originCode = originCode;
        this.destinationCode = destinationCode;
        this.aircraftRegistrationNumber = aircraftRegistrationNumber;
        this.aircraftModel = aircraftModel;
        this.revision = revision;
    }

    public void changeStatus(FlightStatus newStatus) {
        Objects.requireNonNull(newStatus, "New status must not be null");
        if (!status.canTransitionTo(newStatus)) {
            throw new InvalidFlightStatusTransitionException(status, newStatus);
        }
        this.status = newStatus;
        this.revision++;
    }

    public boolean overlaps(OffsetDateTime otherDeparture, OffsetDateTime otherArrival) {
        return departureDateTime.isBefore(otherArrival) && arrivalDateTime.isAfter(otherDeparture);
    }

    public String getId() { return id; }
    public OffsetDateTime getDepartureDateTime() { return departureDateTime; }
    public OffsetDateTime getArrivalDateTime() { return arrivalDateTime; }
    public FlightStatus getStatus() { return status; }
    public String getOriginCode() { return originCode; }
    public String getDestinationCode() { return destinationCode; }
    public String getAircraftRegistrationNumber() { return aircraftRegistrationNumber; }
    public String getAircraftModel() { return aircraftModel; }
    public long getRevision() { return revision; }

    public Duration getDuration() {
        return Duration.between(departureDateTime, arrivalDateTime);
    }

    public double getDurationHours() {
        return getDuration().toSeconds() / 3600.0;
    }
}
