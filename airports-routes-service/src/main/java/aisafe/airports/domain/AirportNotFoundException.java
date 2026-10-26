package aisafe.airports.domain;

import aisafe.shared.domain.ResourceNotFoundException;

/**
 * Raised when the requested airport cannot be found.
 */
public class AirportNotFoundException extends ResourceNotFoundException {
    public AirportNotFoundException(String iataCode) {
        super("Airport with IATA code '" + iataCode + "' not found.");
    }
}
