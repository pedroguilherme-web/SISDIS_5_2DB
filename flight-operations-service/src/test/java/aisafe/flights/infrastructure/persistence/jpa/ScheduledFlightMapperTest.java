package aisafe.flights.infrastructure.persistence.jpa;

import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ScheduledFlightMapperTest {

    @Test
    void shouldMapJpaEntityToDomainSuccessfully() {
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime arrival = departure.plusHours(2);

        ScheduledFlightJpaEntity entity = new ScheduledFlightJpaEntity(
                departure, arrival, FlightStatus.COMPLETED, "LIS", "OPO", "CS-TRP"
        );
        entity.setId(5L);

        ScheduledFlight domainFlight = ScheduledFlightMapper.toDomain(entity);

        assertNotNull(domainFlight);
        assertEquals(5L, domainFlight.getId());
        assertEquals(departure, domainFlight.getDepartureDateTime());
        assertEquals(arrival, domainFlight.getArrivalDateTime());
        assertEquals(FlightStatus.COMPLETED, domainFlight.getStatus());
        assertEquals("LIS", domainFlight.getOriginCode());
        assertEquals("OPO", domainFlight.getDestinationCode());
        assertEquals("CS-TRP", domainFlight.getAircraftRegistrationNumber());
    }

    @Test
    void shouldReturnNullWhenEntityIsNull() {
        ScheduledFlight domainFlight = ScheduledFlightMapper.toDomain(null);
        assertNull(domainFlight);
    }
}