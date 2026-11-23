package aisafe.flights.domain;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ScheduledFlightTest {
    private static final OffsetDateTime DEPARTURE = OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime ARRIVAL = DEPARTURE.plusHours(2).plusMinutes(30);

    private static ScheduledFlight newFlight(FlightStatus status) {
        return new ScheduledFlight(DEPARTURE, ARRIVAL, status, "LIS", "OPO", "CS-TKA", "A320");
    }

    @Test
    void shouldCreateFlightWithGeneratedUuidWhenAllInputsAreValid() {
        ScheduledFlight flight = newFlight(FlightStatus.SCHEDULED);

        assertDoesNotThrow(() -> UUID.fromString(flight.getId()));
        assertEquals(DEPARTURE, flight.getDepartureDateTime());
        assertEquals(ARRIVAL, flight.getArrivalDateTime());
        assertEquals(FlightStatus.SCHEDULED, flight.getStatus());
        assertEquals("LIS", flight.getOriginCode());
        assertEquals("OPO", flight.getDestinationCode());
        assertEquals("CS-TKA", flight.getAircraftRegistrationNumber());
        assertEquals("A320", flight.getAircraftModel());
    }

    @Test
    void ensureEachNewFlightGetsADifferentIdSoReplicasNeverCollide() {
        assertNotEquals(newFlight(FlightStatus.SCHEDULED).getId(), newFlight(FlightStatus.SCHEDULED).getId());
    }

    @Test
    void shouldKeepTheGivenIdWhenRebuildingAnExistingFlight() {
        ScheduledFlight flight = new ScheduledFlight("flight-1", DEPARTURE, ARRIVAL, FlightStatus.COMPLETED, "LIS", "OPO", "CS-TKA", null);

        assertEquals("flight-1", flight.getId());
        assertNull(flight.getAircraftModel());
    }

    @Test
    void shouldRejectBlankId() {
        assertThrows(InvalidFlightScheduleException.class,
                () -> new ScheduledFlight(" ", DEPARTURE, ARRIVAL, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TKA", null));
    }

    @Test
    void shouldRejectDepartureAfterArrival() {
        InvalidFlightScheduleException ex = assertThrows(InvalidFlightScheduleException.class,
                () -> new ScheduledFlight(ARRIVAL, DEPARTURE, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TKA", null));
        assertEquals("Departure must be before arrival.", ex.getMessage());
    }

    @Test
    void shouldRejectDepartureEqualToArrival() {
        assertThrows(InvalidFlightScheduleException.class,
                () -> new ScheduledFlight(DEPARTURE, DEPARTURE, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TKA", null));
    }

    @Test
    void shouldRejectMissingDates() {
        assertThrows(InvalidFlightScheduleException.class,
                () -> new ScheduledFlight(null, ARRIVAL, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TKA", null));
        assertThrows(InvalidFlightScheduleException.class,
                () -> new ScheduledFlight(DEPARTURE, null, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TKA", null));
    }

    @Test
    void shouldRejectMissingStatus() {
        assertThrows(InvalidFlightScheduleException.class,
                () -> new ScheduledFlight(DEPARTURE, ARRIVAL, null, "LIS", "OPO", "CS-TKA", null));
    }

    @Test
    void shouldRejectBlankAirportsAndRegistration() {
        assertThrows(InvalidFlightScheduleException.class,
                () -> new ScheduledFlight(DEPARTURE, ARRIVAL, FlightStatus.SCHEDULED, " ", "OPO", "CS-TKA", null));
        assertThrows(InvalidFlightScheduleException.class,
                () -> new ScheduledFlight(DEPARTURE, ARRIVAL, FlightStatus.SCHEDULED, "LIS", "", "CS-TKA", null));
        assertThrows(InvalidFlightScheduleException.class,
                () -> new ScheduledFlight(DEPARTURE, ARRIVAL, FlightStatus.SCHEDULED, "LIS", "OPO", " ", null));
    }

    @Test
    void shouldRejectSameOriginAndDestination() {
        assertThrows(InvalidFlightScheduleException.class,
                () -> new ScheduledFlight(DEPARTURE, ARRIVAL, FlightStatus.SCHEDULED, "LIS", "lis", "CS-TKA", null));
    }

    @Test
    void shouldFollowTheLifecycleUntilCompleted() {
        ScheduledFlight flight = newFlight(FlightStatus.SCHEDULED);

        flight.changeStatus(FlightStatus.DELAYED);
        flight.changeStatus(FlightStatus.IN_FLIGHT);
        flight.changeStatus(FlightStatus.COMPLETED);

        assertEquals(FlightStatus.COMPLETED, flight.getStatus());
    }

    @Test
    void shouldRejectInvalidTransitionAndKeepCurrentStatus() {
        ScheduledFlight flight = newFlight(FlightStatus.COMPLETED);

        InvalidFlightStatusTransitionException ex = assertThrows(InvalidFlightStatusTransitionException.class,
                () -> flight.changeStatus(FlightStatus.SCHEDULED));

        assertEquals(FlightStatus.COMPLETED, flight.getStatus());
        assertTrue(ex.getMessage().contains("COMPLETED to SCHEDULED"));
    }

    @Test
    void shouldComputeDurationInHours() {
        assertEquals(2.5, newFlight(FlightStatus.SCHEDULED).getDurationHours(), 1e-9);
    }

    @Test
    void shouldDetectOverlappingIntervals() {
        ScheduledFlight flight = newFlight(FlightStatus.SCHEDULED);

        assertTrue(flight.overlaps(DEPARTURE.plusHours(1), ARRIVAL.plusHours(1)));
        assertTrue(flight.overlaps(DEPARTURE.minusHours(1), DEPARTURE.plusMinutes(1)));
        assertFalse(flight.overlaps(ARRIVAL, ARRIVAL.plusHours(1)), "back-to-back flights do not overlap");
        assertFalse(flight.overlaps(DEPARTURE.minusHours(2), DEPARTURE));
    }
}
