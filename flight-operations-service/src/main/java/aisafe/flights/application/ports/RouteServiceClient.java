package aisafe.flights.application.ports;

import aisafe.flights.application.dtos.RouteDTO;

import java.util.Optional;

public interface RouteServiceClient {
    Optional<RouteDTO> findRoute(String originIataCode, String destinationIataCode);
}
