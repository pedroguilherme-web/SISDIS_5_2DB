package aisafe.flights.infrastructure.readmodel;

import aisafe.flights.domain.FlightStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AirplaneMoodTest {
    @ParameterizedTest(name = "aircraft={0}, flight={1}, completed={2}, hours={3} -> {4}")
    @CsvSource(nullValues = "null", value = {
            "UNDER_MAINTENANCE, IN_FLIGHT, 5, 2000, UNDER_CARE",
            "INACTIVE,          COMPLETED, 5,   10, GRUMPY",
            "AVAILABLE,         IN_FLIGHT, 0,    0, SOARING",
            "null,              DELAYED,   0,    0, IMPATIENT",
            "AVAILABLE,         CANCELED,  3,   20, DISAPPOINTED",
            "AVAILABLE,         COMPLETED, 400, 1200, PROUD",
            "AVAILABLE,         COMPLETED, 2,    3, HAPPY",
            "null,              SCHEDULED, 0,    0, EXCITED",
            "null,              null,      0,    0, EXCITED"
    })
    void shouldPickTheFirstMatchingRule(String aircraftStatus, FlightStatus flightStatus, long completed, double hours,
                                        String expectedMood) {
        assertEquals(expectedMood, AirplaneMood.of(aircraftStatus, flightStatus, completed, hours).mood());
    }
}
