package aisafe.flights.domain;

import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ScheduledFlightTest {

    @Test
    void shouldCreateFlightWhenAllInputsAreValid() {
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime arrival = departure.plusHours(3);

        ScheduledFlight flight = new ScheduledFlight(
                departure,
                arrival,
                FlightStatus.SCHEDULED,
                "LIS",
                "OPO",
                "CS-TRP"
        );

        assertNull(flight.getId()); // O primeiro construtor não define ID
        assertEquals("LIS", flight.getOriginCode());
        assertEquals("OPO", flight.getDestinationCode());
        assertEquals("CS-TRP", flight.getAircraftRegistrationNumber());
        assertEquals(FlightStatus.SCHEDULED, flight.getStatus());
        assertEquals(departure, flight.getDepartureDateTime());
        assertEquals(arrival, flight.getArrivalDateTime());
        assertEquals(3, flight.getDuration().toHours());
    }

    @Test
    void shouldCreateFlightWithIdWhenUsingSecondConstructor() {
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime arrival = departure.plusHours(2);

        ScheduledFlight flight = new ScheduledFlight(
                10L,
                departure,
                arrival,
                FlightStatus.COMPLETED,
                "MAD",
                "LIS",
                "CS-TKA"
        );

        assertEquals(10L, flight.getId());
        assertEquals(FlightStatus.COMPLETED, flight.getStatus());
    }

    @Test
    void shouldThrowExceptionWhenDepartureIsAfterArrival() {
        OffsetDateTime arrival = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime departure = arrival.plusHours(2); // Invalido: Partida depois da chegada

        InvalidFlightScheduleException exception = assertThrows(InvalidFlightScheduleException.class, () -> {
            new ScheduledFlight(departure, arrival, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TRP");
        });

        assertEquals("Departure must be before arrival.", exception.getMessage());
    }

    @Test
    void shouldThrowExceptionWhenDepartureIsNull() {
        OffsetDateTime arrival = OffsetDateTime.now(ZoneOffset.UTC);

        assertThrows(InvalidFlightScheduleException.class, () -> {
            new ScheduledFlight(null, arrival, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TRP");
        });
    }

    @Test
    void shouldThrowExceptionWhenArrivalIsNull() {
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC);

        assertThrows(InvalidFlightScheduleException.class, () -> {
            new ScheduledFlight(departure, null, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TRP");
        });
    }

    @Test
    void shouldThrowExceptionWhenStatusIsNull() {
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime arrival = departure.plusHours(2);

        assertThrows(InvalidFlightScheduleException.class, () -> {
            new ScheduledFlight(departure, arrival, null, "LIS", "OPO", "CS-TRP");
        });
    }

    @Test
    void shouldThrowExceptionWhenOriginCodeIsBlank() {
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime arrival = departure.plusHours(2);

        assertThrows(InvalidFlightScheduleException.class, () -> {
            new ScheduledFlight(departure, arrival, FlightStatus.SCHEDULED, "   ", "OPO", "CS-TRP");
        });
    }

    @Test
    void shouldThrowExceptionWhenDestinationCodeIsBlank() {
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime arrival = departure.plusHours(2);

        assertThrows(InvalidFlightScheduleException.class, () -> {
            new ScheduledFlight(departure, arrival, FlightStatus.SCHEDULED, "LIS", "", "CS-TRP");
        });
    }

    @Test
    void shouldThrowExceptionWhenAircraftRegistrationIsBlank() {
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime arrival = departure.plusHours(2);

        assertThrows(InvalidFlightScheduleException.class, () -> {
            new ScheduledFlight(departure, arrival, FlightStatus.SCHEDULED, "LIS", "OPO", null);
        });
    }

    @Test
    void shouldReturnZeroDurationWhenDatesAreMissing() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        ScheduledFlight flight = new ScheduledFlight(
                now, now.plusHours(1), FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TKA"
        );

        org.springframework.test.util.ReflectionTestUtils.setField(flight, "departureDateTime", null);

        assertEquals(0, flight.getDuration().toMillis());
    }
}