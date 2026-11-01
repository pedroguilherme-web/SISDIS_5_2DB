package aisafe.flights.infrastructure.persistence.jpa;

import aisafe.flights.domain.FlightStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ScheduledFlightJpaEntityTest {

    @Test
    void shouldCreateEntityAndMapFieldsCorrectly() {
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime arrival = departure.plusHours(2);

        ScheduledFlightJpaEntity entity = new ScheduledFlightJpaEntity(
                departure, arrival, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TKA"
        );

        assertNull(entity.getId());
        assertEquals(departure, entity.getDepartureDateTime());
        assertEquals(arrival, entity.getArrivalDateTime());
        assertEquals(FlightStatus.SCHEDULED, entity.getStatus());
        assertEquals("LIS", entity.getOriginCode());
        assertEquals("OPO", entity.getDestinationCode());
        assertEquals("CS-TKA", entity.getAircraftRegistrationNumber());
        assertNull(entity.getVersion());
    }

    @Test
    void shouldAllowSettingIdAndVersionViaSetters() {
        ScheduledFlightJpaEntity entity = new ScheduledFlightJpaEntity();

        entity.setId(1L);
        entity.setVersion(0L);
        entity.setStatus(FlightStatus.IN_FLIGHT);

        assertEquals(1L, entity.getId());
        assertEquals(0L, entity.getVersion());
        assertEquals(FlightStatus.IN_FLIGHT, entity.getStatus());
    }
}