package aisafe.flights.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class FlightStatusTest {
    @Test
    void ensureAllEnumValuesExist() {
        assertArrayEquals(
                new FlightStatus[]{FlightStatus.SCHEDULED, FlightStatus.DELAYED, FlightStatus.IN_FLIGHT,
                        FlightStatus.COMPLETED, FlightStatus.CANCELED},
                FlightStatus.values());
    }

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "SCHEDULED, DELAYED",
            "SCHEDULED, IN_FLIGHT",
            "SCHEDULED, CANCELED",
            "DELAYED, IN_FLIGHT",
            "DELAYED, CANCELED",
            "IN_FLIGHT, COMPLETED"
    })
    void ensureLifecycleTransitionsAreAllowed(FlightStatus from, FlightStatus to) {
        assertTrue(from.canTransitionTo(to));
    }

    @ParameterizedTest(name = "{0} -> {1} is rejected")
    @CsvSource({
            "SCHEDULED, SCHEDULED",
            "SCHEDULED, COMPLETED",
            "DELAYED, SCHEDULED",
            "IN_FLIGHT, CANCELED",
            "IN_FLIGHT, SCHEDULED",
            "COMPLETED, SCHEDULED",
            "COMPLETED, CANCELED",
            "CANCELED, SCHEDULED",
            "CANCELED, IN_FLIGHT"
    })
    void ensureOutOfOrderTransitionsAreRejected(FlightStatus from, FlightStatus to) {
        assertFalse(from.canTransitionTo(to));
    }

    @Test
    void ensureOnlyCompletedAndCanceledAreTerminal() {
        assertTrue(FlightStatus.COMPLETED.isTerminal());
        assertTrue(FlightStatus.CANCELED.isTerminal());
        assertFalse(FlightStatus.SCHEDULED.isTerminal());
        assertFalse(FlightStatus.DELAYED.isTerminal());
        assertFalse(FlightStatus.IN_FLIGHT.isTerminal());
    }

    @Test
    void ensureTransitionToNullIsRejected() {
        assertFalse(FlightStatus.SCHEDULED.canTransitionTo(null));
    }

    @Test
    void ensureValueOfThrowsExceptionForInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> FlightStatus.valueOf("LANDED"));
    }
}
