package aisafe.flights.infrastructure.persistence.jpa;

import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ModelUtilizationData;
import aisafe.flights.domain.RouteUtilizationData;
import aisafe.flights.domain.ScheduledFlight;
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
@Import({ScheduledFlightJpaRepository.class, AircraftScheduleLocks.class})
class ScheduledFlightJpaRepositoryTest {
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private ScheduledFlightJpaRepository repository;

    private ScheduledFlight save(OffsetDateTime departure, double hours, FlightStatus status,
                                 String origin, String destination, String registration, String model) {
        return repository.save(new ScheduledFlight(departure, departure.plusMinutes((long) (hours * 60)), status,
                origin, destination, registration, model));
    }

    @Test
    void shouldSaveAndFindByUuid() {
        ScheduledFlight saved = save(T0, 2, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TKA", "A320");

        Optional<ScheduledFlight> found = repository.findById(saved.getId());

        assertTrue(found.isPresent());
        assertEquals("A320", found.get().getAircraftModel());
        assertEquals(1, repository.count());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void shouldUpdateStatusOfAnExistingFlightInsteadOfInsertingAgain() {
        ScheduledFlight flight = save(T0, 2, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TKA", "A320");

        flight.changeStatus(FlightStatus.IN_FLIGHT);
        repository.save(flight);

        assertEquals(1, repository.count());
        assertEquals(FlightStatus.IN_FLIGHT, repository.findById(flight.getId()).orElseThrow().getStatus());
    }

    @Test
    void shouldDeleteFlight() {
        ScheduledFlight flight = save(T0, 2, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TKA", "A320");

        repository.delete(flight);

        assertTrue(repository.findById(flight.getId()).isEmpty());
    }

    @Test
    void shouldListAircraftFlightsInChronologicalOrder() {
        save(T0.plusDays(1), 3, FlightStatus.SCHEDULED, "OPO", "MAD", "CS-TKA", "A320");
        save(T0, 2, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TKA", "A320");
        save(T0, 2, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-OTHER", "A320");

        List<ScheduledFlight> flights = repository.findByAircraftRegistration("CS-TKA");

        assertEquals(List.of("LIS", "OPO"), flights.stream().map(ScheduledFlight::getOriginCode).toList());
        assertTrue(repository.existsByAircraftRegistration("CS-TKA"));
        assertFalse(repository.existsByAircraftRegistration("CS-NONE"));
    }

    @Test
    void shouldDetectOverlapsButIgnoreCanceledAndBackToBackFlights() {
        save(T0, 3, FlightStatus.SCHEDULED, "LIS", "MAD", "CS-TKA", "A320");
        save(T0.plusDays(1), 3, FlightStatus.CANCELED, "LIS", "MAD", "CS-TKA", "A320");

        assertTrue(repository.existsByOverlappingSchedule("CS-TKA", T0.plusHours(1), T0.plusHours(4)));
        assertFalse(repository.existsByOverlappingSchedule("CS-TKA", T0.plusHours(3), T0.plusHours(5)), "back-to-back");
        assertFalse(repository.existsByOverlappingSchedule("CS-TKA", T0.plusDays(1), T0.plusDays(1).plusHours(1)), "canceled");
        assertFalse(repository.existsByOverlappingSchedule("CS-OTHER", T0, T0.plusHours(1)), "other aircraft");
    }

    @Test
    void shouldSumOnlyCompletedHours() {
        save(T0.minusDays(3), 2, FlightStatus.COMPLETED, "LIS", "OPO", "CS-TKA", "A320");
        save(T0.minusDays(1), 1.5, FlightStatus.COMPLETED, "OPO", "MAD", "CS-TKA", "A320");
        save(T0.plusDays(1), 5, FlightStatus.SCHEDULED, "MAD", "LIS", "CS-TKA", "A320");

        assertEquals(3.5, repository.calculateTotalOperationalHoursByRegistration("CS-TKA"), 0.01);
        assertEquals(1.5, repository.calculateOperationalHoursSince("CS-TKA", T0.minusDays(2)), 0.01);
        assertEquals(0.0, repository.calculateTotalOperationalHoursByRegistration("CS-NONE"), 0.01);
    }

    @Test
    void shouldCountFlightsPerRoute() {
        save(T0, 1, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-A", "A320");
        save(T0, 1, FlightStatus.COMPLETED, "LIS", "OPO", "CS-B", "A320");
        save(T0, 1, FlightStatus.COMPLETED, "OPO", "LIS", "CS-C", "A320");

        assertEquals(2, repository.countByRoute("LIS", "OPO"));
        assertEquals(0, repository.countByRoute("FAO", "LIS"));
    }

    @Test
    void shouldRankAircraftModelsByCompletedHoursAndFlights() {
        save(T0, 4, FlightStatus.COMPLETED, "LIS", "OPO", "CS-A", "A320");
        save(T0.plusDays(1), 1, FlightStatus.COMPLETED, "LIS", "OPO", "CS-B", "B737");
        save(T0.plusDays(2), 1, FlightStatus.COMPLETED, "LIS", "OPO", "CS-B", "B737");
        save(T0.plusDays(3), 9, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-C", "E190");

        List<ModelUtilizationData> byHours = repository.findModelUtilizationByFlightHours();
        List<ModelUtilizationData> byFlights = repository.findModelUtilizationByAssignments();

        assertEquals("A320", byHours.getFirst().modelName());
        assertEquals(4.0, byHours.getFirst().value(), 0.01);
        assertEquals("B737", byFlights.getFirst().modelName());
        assertEquals(2.0, byFlights.getFirst().value(), 0.01);
        assertEquals(2, byHours.size(), "scheduled flights do not count");
    }

    @Test
    void shouldReportCompletedFlightsPerRouteInsideTheDateWindow() {
        save(T0, 1, FlightStatus.COMPLETED, "LIS", "OPO", "CS-A", "A320");
        save(T0.plusDays(1), 1, FlightStatus.COMPLETED, "LIS", "OPO", "CS-A", "A320");
        save(T0.plusDays(2), 1, FlightStatus.COMPLETED, "OPO", "MAD", "CS-A", "A320");
        save(T0.plusDays(30), 1, FlightStatus.COMPLETED, "FAO", "LIS", "CS-A", "A320");

        List<RouteUtilizationData> all = repository.findRouteUtilization(null, null);
        List<RouteUtilizationData> window = repository.findRouteUtilization(T0.minusDays(1), T0.plusDays(5));

        assertEquals(3, all.size());
        assertEquals(new RouteUtilizationData("LIS", "OPO", 2), all.getFirst());
        assertEquals(2, window.size());
    }
}
