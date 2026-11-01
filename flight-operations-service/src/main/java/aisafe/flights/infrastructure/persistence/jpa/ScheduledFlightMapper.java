package aisafe.flights.infrastructure.persistence.jpa;

import aisafe.flights.domain.ScheduledFlight;

public class ScheduledFlightMapper {

    private ScheduledFlightMapper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static ScheduledFlight toDomain(ScheduledFlightJpaEntity entity) {
        if (entity == null) return null;

        return new ScheduledFlight(
                entity.getId(),
                entity.getDepartureDateTime(),
                entity.getArrivalDateTime(),
                entity.getStatus(),
                entity.getOriginCode(),
                entity.getDestinationCode(),
                entity.getAircraftRegistrationNumber()
        );
    }
}