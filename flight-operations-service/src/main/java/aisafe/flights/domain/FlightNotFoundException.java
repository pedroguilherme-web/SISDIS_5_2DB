package aisafe.flights.domain;

import aisafe.shared.domain.ResourceNotFoundException;

public class FlightNotFoundException extends ResourceNotFoundException {
    public FlightNotFoundException(String flightId) {
        super("Flight not found: " + flightId);
    }
}
