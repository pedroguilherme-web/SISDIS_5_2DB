package aisafe.airports.domain;

import aisafe.shared.domain.ResourceNotFoundException;

public class AirportPhotoNotFoundException extends ResourceNotFoundException {
    public AirportPhotoNotFoundException(String iataCode) {
        super("Airport '" + iataCode + "' has no photo.");
    }
}
