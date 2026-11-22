package aisafe.flights.domain;

import aisafe.shared.domain.DomainException;

public class InvalidFlightScheduleException extends DomainException {
    public InvalidFlightScheduleException(String message) {
        super(message);
    }
}
