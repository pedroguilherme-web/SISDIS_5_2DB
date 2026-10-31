package aisafe.flights.domain;

import java.time.OffsetDateTime;
import java.time.Duration;

public class ScheduledFlight {
    private Long id;
    private OffsetDateTime departureDateTime;
    private OffsetDateTime arrivalDateTime;
    private FlightStatus status;
    private String originCode; // Alterado de IataCode
    private String destinationCode; // Alterado de IataCode
    private String aircraftRegistrationNumber; // Alterado de RegistrationNumber

    public ScheduledFlight(OffsetDateTime departureDateTime, OffsetDateTime arrivalDateTime,
                           FlightStatus status, String originCode, String destinationCode,
                           String aircraftRegistrationNumber) {
        if (departureDateTime == null) throw new InvalidFlightScheduleException("Departure date/time must not be null.");
        if (arrivalDateTime == null) throw new InvalidFlightScheduleException("Arrival date/time must not be null.");
        if (status == null) throw new InvalidFlightScheduleException("Flight status must not be null.");
        if (originCode == null || originCode.isBlank()) throw new InvalidFlightScheduleException("Origin code must not be null.");
        if (destinationCode == null || destinationCode.isBlank()) throw new InvalidFlightScheduleException("Destination code must not be null.");
        if (aircraftRegistrationNumber == null || aircraftRegistrationNumber.isBlank()) throw new InvalidFlightScheduleException("Aircraft registration must not be null.");
        if (!departureDateTime.isBefore(arrivalDateTime)) throw new InvalidFlightScheduleException("Departure must be before arrival.");

        this.departureDateTime = departureDateTime;
        this.arrivalDateTime = arrivalDateTime;
        this.status = status;
        this.originCode = originCode;
        this.destinationCode = destinationCode;
        this.aircraftRegistrationNumber = aircraftRegistrationNumber;
    }

    public ScheduledFlight(Long id, OffsetDateTime departureDateTime, OffsetDateTime arrivalDateTime,
                           FlightStatus status, String originCode, String destinationCode,
                           String aircraftRegistrationNumber) {
        this(departureDateTime, arrivalDateTime, status, originCode, destinationCode, aircraftRegistrationNumber);
        this.id = id;
    }

    public Long getId() { return id; }
    public OffsetDateTime getDepartureDateTime() { return departureDateTime; }
    public OffsetDateTime getArrivalDateTime() { return arrivalDateTime; }
    public FlightStatus getStatus() { return status; }
    public String getOriginCode() { return originCode; }
    public String getDestinationCode() { return destinationCode; }
    public String getAircraftRegistrationNumber() { return aircraftRegistrationNumber; }

    public Duration getDuration() {
        if (departureDateTime == null || arrivalDateTime == null) return Duration.ZERO;
        return Duration.between(departureDateTime, arrivalDateTime);
    }
}