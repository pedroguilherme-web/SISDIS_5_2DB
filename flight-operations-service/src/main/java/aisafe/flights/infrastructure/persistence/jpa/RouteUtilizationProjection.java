package aisafe.flights.infrastructure.persistence.jpa;

public interface RouteUtilizationProjection {
    String getOriginCode();
    String getDestinationCode();
    Long getFlightCount();
}
