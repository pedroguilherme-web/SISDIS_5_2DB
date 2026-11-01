package aisafe.flights.infrastructure.persistence.jpa;

import aisafe.flights.domain.FlightStatus;
import org.junit.jupiter.api.BeforeEach;
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
class ScheduledFlightPersistenceTest {

    @Autowired
    private SpringDataScheduledFlightRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void ensureCalculateTotalOperationalHoursByRegistrationWorks() {
        OffsetDateTime departure1 = OffsetDateTime.now().minusDays(1);
        OffsetDateTime arrival1 = departure1.plusHours(1).plusMinutes(30);

        ScheduledFlightJpaEntity flight1 = new ScheduledFlightJpaEntity(
                departure1, arrival1, FlightStatus.COMPLETED, "LIS", "MAD", "CS-TTB"
        );
        repository.save(flight1);

        OffsetDateTime departure2 = OffsetDateTime.now().minusHours(5);
        OffsetDateTime arrival2 = departure2.plusHours(2).plusMinutes(15);

        ScheduledFlightJpaEntity flight2 = new ScheduledFlightJpaEntity(
                departure2, arrival2, FlightStatus.COMPLETED, "LIS", "MAD", "CS-TTB"
        );
        repository.save(flight2);

        repository.flush();

        Double totalHours = repository.calculateTotalOperationalHoursByRegistration("CS-TTB");

        assertNotNull(totalHours);
        assertEquals(3.75, totalHours, 0.01);
    }
}