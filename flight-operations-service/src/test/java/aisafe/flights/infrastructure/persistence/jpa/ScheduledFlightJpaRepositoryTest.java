package aisafe.flights.infrastructure.persistence.jpa;

import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("jpa")
@Import(ScheduledFlightJpaRepository.class)
class ScheduledFlightJpaRepositoryTest {

    @Autowired
    private ScheduledFlightJpaRepository repository;

    private OffsetDateTime baseTime;

    @BeforeEach
    void setUp() {
        baseTime = OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    }

    @Test
    void shouldSaveAndFindScheduledFlightById() {
        ScheduledFlight flight = new ScheduledFlight(
                baseTime,
                baseTime.plusHours(2),
                FlightStatus.SCHEDULED,
                "LIS",
                "OPO",
                "CS-TKA"
        );

        ScheduledFlight saved = repository.save(flight);
        assertNotNull(saved.getId());

        Optional<ScheduledFlight> found = repository.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("LIS", found.get().getOriginCode());
        assertEquals("OPO", found.get().getDestinationCode());
        assertEquals("CS-TKA", found.get().getAircraftRegistrationNumber());
    }

    @Test
    void shouldFindFlightsByAircraftRegistration() {
        repository.save(new ScheduledFlight(
                baseTime, baseTime.plusHours(2),
                FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TKA"
        ));
        repository.save(new ScheduledFlight(
                baseTime.plusDays(1), baseTime.plusDays(1).plusHours(3),
                FlightStatus.SCHEDULED, "OPO", "MAD", "CS-TKA"
        ));

        List<ScheduledFlight> flights = repository.findByAircraftRegistration("CS-TKA");
        assertEquals(2, flights.size());
    }

    @Test
    void shouldDetectOverlappingSchedule() {
        repository.save(new ScheduledFlight(
                baseTime, baseTime.plusHours(3),
                FlightStatus.SCHEDULED, "LIS", "MAD", "CS-TKA"
        ));

        boolean overlaps = repository.existsByOverlappingSchedule(
                "CS-TKA",
                baseTime.plusHours(1),
                baseTime.plusHours(4)
        );
        boolean nonOverlapping = repository.existsByOverlappingSchedule(
                "CS-TKA",
                baseTime.plusHours(4),
                baseTime.plusHours(6)
        );

        assertTrue(overlaps);
        assertFalse(nonOverlapping);
    }

    @Test
    void shouldCalculateTotalOperationalHoursByRegistration() {
        repository.save(new ScheduledFlight(
                baseTime.minusDays(2), baseTime.minusDays(2).plusHours(2),
                FlightStatus.COMPLETED, "LIS", "OPO", "CS-TKA"
        ));
        repository.save(new ScheduledFlight(
                baseTime.minusDays(1), baseTime.minusDays(1).plusHours(3),
                FlightStatus.COMPLETED, "OPO", "MAD", "CS-TKA"
        ));

        Double totalHours = repository.calculateTotalOperationalHoursByRegistration("CS-TKA");
        assertNotNull(totalHours);
        assertEquals(5.0, totalHours, 0.01);
    }
}