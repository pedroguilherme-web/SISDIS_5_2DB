package aisafe.flights.infrastructure.persistence.jpa;

import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("jpa")
@Transactional
class ScheduledFlightJpaRepositoryAdditionalTest {

    @Autowired
    private ScheduledFlightRepository domainRepository;

    @Test
    void ensureScheduledFlightJpaEntityCoverage() {
        OffsetDateTime now = OffsetDateTime.now();
        ScheduledFlightJpaEntity entity = new ScheduledFlightJpaEntity();
        entity.setId(99L);
        entity.setDepartureDateTime(now);
        entity.setArrivalDateTime(now.plusHours(1));
        entity.setStatus(FlightStatus.SCHEDULED);
        entity.setVersion(1L);

        assertEquals(99L, entity.getId());
        assertEquals(now, entity.getDepartureDateTime());
        assertEquals(now.plusHours(1), entity.getArrivalDateTime());
        assertEquals(FlightStatus.SCHEDULED, entity.getStatus());
        assertEquals(1L, entity.getVersion());
    }

    @Test
    void ensureDeleteWithNullIdDoesNotThrowAndDoesNothing() {
        ScheduledFlight flight = new ScheduledFlight(
                null,
                OffsetDateTime.now(),
                OffsetDateTime.now().plusHours(2),
                FlightStatus.SCHEDULED,
                "OPO",
                "LIS",
                "CS-TPA"
        );
        assertDoesNotThrow(() -> domainRepository.delete(flight));
    }
}