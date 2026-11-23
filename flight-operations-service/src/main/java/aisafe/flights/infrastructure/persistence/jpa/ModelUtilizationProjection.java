package aisafe.flights.infrastructure.persistence.jpa;

public interface ModelUtilizationProjection {
    String getModelName();
    Number getUtilizationValue();
}
