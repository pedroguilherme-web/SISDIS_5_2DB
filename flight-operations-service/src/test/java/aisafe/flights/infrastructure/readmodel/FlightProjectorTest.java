package aisafe.flights.infrastructure.readmodel;

import aisafe.flights.application.events.FlightEvent;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.infrastructure.persistence.jpa.readmodel.AircraftActivityEntity;
import aisafe.flights.infrastructure.persistence.jpa.readmodel.AircraftActivityRepository;
import aisafe.flights.infrastructure.persistence.jpa.readmodel.FlightViewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("jpa")
@Import({FlightProjector.class, FlightQueryService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class FlightProjectorTest {
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 12, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    @Autowired private FlightProjector projector;
    @Autowired private FlightQueryService queries;
    @Autowired private FlightViewRepository flights;
    @Autowired private AircraftActivityRepository activities;

    @BeforeEach
    void clean() {
        flights.deleteAll();
        activities.deleteAll();
    }

    private static ScheduledFlight flight(String id, double hours) {
        return new ScheduledFlight(id, T0, T0.plusMinutes((long) (hours * 60)), FlightStatus.SCHEDULED,
                "LIS", "OPO", "CS-TRP", "A320");
    }

    private static FlightEvent advance(ScheduledFlight flight, FlightStatus to) {
        FlightStatus previous = flight.getStatus();
        flight.changeStatus(to);
        return FlightEvent.statusChanged(flight, previous);
    }

    @Test
    void shouldProjectAScheduledFlight() {
        assertTrue(projector.apply(FlightEvent.scheduled(flight("f-1", 2))));

        var view = queries.flight("f-1");
        assertEquals(FlightStatus.SCHEDULED, view.status());
        assertEquals(2.0, view.durationHours(), 1e-9);
        assertEquals("EXCITED", queries.mood("CS-TRP").mood());
    }

    @Test
    void shouldIgnoreADuplicatedEvent() {
        FlightEvent scheduled = FlightEvent.scheduled(flight("f-1", 2));

        assertTrue(projector.apply(scheduled));
        assertFalse(projector.apply(scheduled), "same event delivered twice (in-process and through the broker)");
        assertEquals(1, flights.count());
    }

    @Test
    void shouldIgnoreAnEventThatArrivesAfterANewerOne() {
        ScheduledFlight flight = flight("f-1", 2);
        FlightEvent scheduled = FlightEvent.scheduled(flight);
        FlightEvent inFlight = advance(flight, FlightStatus.IN_FLIGHT);

        projector.apply(inFlight);
        assertFalse(projector.apply(scheduled), "revision 0 arriving after revision 1 must not move the view back");

        assertEquals(FlightStatus.IN_FLIGHT, queries.flight("f-1").status());
    }

    @Test
    void shouldPreAggregateTheAircraftActivity() {
        ScheduledFlight first = flight("f-1", 1.5);
        ScheduledFlight second = flight("f-2", 0.5);
        ScheduledFlight third = flight("f-3", 9);
        projector.apply(FlightEvent.scheduled(first));
        projector.apply(FlightEvent.scheduled(second));
        projector.apply(FlightEvent.scheduled(third));
        projector.apply(advance(first, FlightStatus.IN_FLIGHT));
        projector.apply(advance(first, FlightStatus.COMPLETED));
        projector.apply(advance(second, FlightStatus.IN_FLIGHT));
        projector.apply(advance(second, FlightStatus.COMPLETED));
        projector.apply(advance(third, FlightStatus.CANCELED));

        var activity = queries.activity("cs-trp");
        assertEquals(3, activity.totalFlights());
        assertEquals(2, activity.completedFlights());
        assertEquals(1, activity.canceledFlights());
        assertEquals(2.0, activity.completedHours(), 1e-9, "canceled and scheduled flights do not count");
        assertEquals(FlightStatus.CANCELED, activity.latestFlightStatus());
        assertEquals("DISAPPOINTED", queries.mood("CS-TRP").mood());
    }

    @Test
    void shouldCombineAircraftStatusFromTheAircraftServiceWithFlightFacts() {
        projector.apply(FlightEvent.scheduled(flight("f-1", 2)));

        projector.applyAircraftStatus("CS-TRP", "A320neo", "UNDER_MAINTENANCE");

        AircraftActivityEntity activity = activities.findById("CS-TRP").orElseThrow();
        assertEquals("UNDER_CARE", activity.getMood());
        assertEquals("A320neo", activity.getAircraftModel());
        assertEquals(1, activity.getTotalFlights(), "flight facts are kept");
    }

    @Test
    void shouldCreateAMoodForAnAircraftWithoutFlights() {
        projector.applyAircraftStatus("CS-NEW", "E190", "INACTIVE");

        assertEquals("GRUMPY", queries.mood("cs-new").mood());
    }

    @Test
    void shouldFilterAndPaginateTheReadModel() {
        projector.apply(FlightEvent.scheduled(flight("f-1", 1)));
        ScheduledFlight completed = flight("f-2", 1);
        projector.apply(FlightEvent.scheduled(completed));
        projector.apply(advance(completed, FlightStatus.IN_FLIGHT));

        assertEquals(2, queries.search("cs-trp", null, null, null, 0, 20).totalElements());
        assertEquals(1, queries.search(null, "lis", "opo", "in_flight", 0, 20).totalElements());
        assertEquals(1, queries.search(null, null, null, null, 0, 1).content().size());
        assertThrows(IllegalArgumentException.class, () -> queries.search(null, null, null, "LANDED", 0, 20));
    }
}
