package aisafe.flights.domain;

import aisafe.shared.domain.DomainException;

public class AircraftIncompatibilityException extends DomainException {
    public AircraftIncompatibilityException(String message) {
        super(message);
    }
}
