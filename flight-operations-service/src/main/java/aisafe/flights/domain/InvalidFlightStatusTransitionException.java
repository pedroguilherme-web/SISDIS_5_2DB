package aisafe.flights.domain;

import aisafe.shared.domain.DomainException;

public class InvalidFlightStatusTransitionException extends DomainException {
    public InvalidFlightStatusTransitionException(FlightStatus from, FlightStatus to) {
        super("Flight cannot change status from " + from + " to " + to
                + ". Allowed: " + from.allowedTransitions() + ".");
    }
}
