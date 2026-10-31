package aisafe.aircrafts.domain;

import java.util.List;

/**
 * Domain interface for interacting with the external Airports & Routes Service.
 */
public interface RouteServiceClient {

    List<CompatibleRouteData> findCompatibleRoutes(double range, int seatCapacity);

    RouteData findRouteByOriginAndDestination(String origin, String destination);
}
