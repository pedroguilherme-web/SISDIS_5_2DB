package aisafe.flights.infrastructure.persistence.jpa;

import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ScheduledFlightMapperTest {
    private static final OffsetDateTime DEPARTURE = OffsetDateTime.of(2026, 12, 1, 8, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime ARRIVAL = DEPARTURE.plusHours(2);

    @Test
    void shouldMapJpaEntityToDomain() {
        ScheduledFlightJpaEntity entity = new ScheduledFlightJpaEntity(
                "f-5", DEPARTURE, ARRIVAL, FlightStatus.COMPLETED, "LIS", "OPO", "CS-TRP", "A320");

        ScheduledFlight flight = ScheduledFlightMapper.toDomain(entity);

        assertEquals("f-5", flight.getId());
        assertEquals(DEPARTURE, flight.getDepartureDateTime());
        assertEquals(ARRIVAL, flight.getArrivalDateTime());
        assertEquals(FlightStatus.COMPLETED, flight.getStatus());
        assertEquals("LIS", flight.getOriginCode());
        assertEquals("OPO", flight.getDestinationCode());
        assertEquals("CS-TRP", flight.getAircraftRegistrationNumber());
        assertEquals("A320", flight.getAircraftModel());
    }

    @Test
    void shouldMapNewDomainFlightToEntityKeepingItsUuidAndNoVersion() {
        ScheduledFlight flight = new ScheduledFlight(DEPARTURE, ARRIVAL, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TRP", "A320");

        ScheduledFlightJpaEntity entity = ScheduledFlightMapper.toNewEntity(flight);

        assertEquals(flight.getId(), entity.getId());
        assertEquals("A320", entity.getAircraftModel());
        assertNull(entity.getVersion(), "a null version tells Spring Data the entity is new");
    }

    @Test
    void shouldCopyOnlyTheMutableStatusOntoAManagedEntity() {
        ScheduledFlightJpaEntity entity = new ScheduledFlightJpaEntity(
                "f-5", DEPARTURE, ARRIVAL, FlightStatus.IN_FLIGHT, "LIS", "OPO", "CS-TRP", "A320");
        ScheduledFlight flight = ScheduledFlightMapper.toDomain(entity);
        flight.changeStatus(FlightStatus.COMPLETED);

        ScheduledFlightMapper.copyMutableState(flight, entity);

        assertEquals(FlightStatus.COMPLETED, entity.getStatus());
        assertEquals("f-5", entity.getId());
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        assertNull(ScheduledFlightMapper.toDomain(null));
    }
}
