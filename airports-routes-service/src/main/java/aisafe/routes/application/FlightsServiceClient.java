package aisafe.routes.application;

import aisafe.airports.domain.IataCode;

public interface FlightsServiceClient {
    long countFlightsByRoute(IataCode origin, IataCode destination);
}
