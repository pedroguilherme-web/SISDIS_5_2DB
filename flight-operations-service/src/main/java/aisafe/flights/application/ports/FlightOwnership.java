package aisafe.flights.application.ports;

import java.util.Optional;

public interface FlightOwnership {
    Optional<String> ownerOf(String aircraftRegistration);
}
