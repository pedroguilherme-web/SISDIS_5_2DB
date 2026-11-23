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
                entity.getAircraftRegistrationNumber(),
                entity.getAircraftModel(),
                entity.getRevision()
        );
    }

    public static ScheduledFlightJpaEntity toNewEntity(ScheduledFlight flight) {
        ScheduledFlightJpaEntity entity = new ScheduledFlightJpaEntity(
                flight.getId(),
                flight.getDepartureDateTime(),
                flight.getArrivalDateTime(),
                flight.getStatus(),
                flight.getOriginCode(),
                flight.getDestinationCode(),
                flight.getAircraftRegistrationNumber(),
                flight.getAircraftModel()
        );
        entity.setRevision(flight.getRevision());
        return entity;
    }

    public static void copyMutableState(ScheduledFlight flight, ScheduledFlightJpaEntity entity) {
        entity.setStatus(flight.getStatus());
        entity.setRevision(flight.getRevision());
    }
}
