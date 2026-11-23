package aisafe.flights.application.ports;

import aisafe.flights.application.dtos.AircraftDTO;

import java.util.Optional;

public interface AircraftServiceClient {
    Optional<AircraftDTO> findByRegistration(String registration);
}
