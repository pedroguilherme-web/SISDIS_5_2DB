package aisafe.flights.infrastructure;

import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightLocalSearchProviderTest {
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 12, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    @Mock
    private ScheduledFlightRepository repository;

    private FlightLocalSearchProvider provider;

    private final ScheduledFlight lisOpoCompleted = new ScheduledFlight("a", T0, T0.plusHours(1), FlightStatus.COMPLETED, "LIS", "OPO", "CS-TRP", "A320");
    private final ScheduledFlight opoMadScheduled = new ScheduledFlight("b", T0.plusDays(1), T0.plusDays(1).plusHours(1), FlightStatus.SCHEDULED, "OPO", "MAD", "CS-TRP", "A320");

    @BeforeEach
    void setUp() {
        provider = new FlightLocalSearchProvider(repository);
    }

    @Test
    void ensureEmptyArrayForNullOrForeignPath() {
        assertEquals("[]", provider.searchLocal(null));
        assertEquals("[]", provider.searchLocal("/api/aircrafts"));
        verifyNoInteractions(repository);
    }

    @Test
    void ensureFiltersByAircraftUsingTheIndexedQuery() {
        when(repository.findByAircraftRegistration("CS-TRP")).thenReturn(List.of(lisOpoCompleted, opoMadScheduled));

        String json = provider.searchLocal("/api/flights?aircraftId=cs-trp");

        assertTrue(json.contains("\"id\":\"a\"") && json.contains("\"id\":\"b\""));
        verify(repository, never()).findAll();
    }

    @Test
    void ensureFiltersByOriginAndStatus() {
        when(repository.findAll()).thenReturn(List.of(lisOpoCompleted, opoMadScheduled));

        String json = provider.searchLocal("/api/flights?origin=LIS&status=completed");

        assertTrue(json.contains("\"id\":\"a\""));
        assertFalse(json.contains("\"id\":\"b\""));
    }

    @Test
    void ensureDatesAreSerializedAsIsoStrings() {
        when(repository.findAll()).thenReturn(List.of(lisOpoCompleted));

        assertTrue(provider.searchLocal("/api/flights").contains("\"departureDateTime\":\"2026-12-01T08:00:00Z\""));
    }

    @Test
    void ensureIllegalStateExceptionOnSerializationFailure() throws JsonProcessingException {
        ObjectMapper failing = mock(ObjectMapper.class);
        when(failing.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
        when(repository.findAll()).thenReturn(List.of());

        FlightLocalSearchProvider withFailingMapper = new FlightLocalSearchProvider(repository, failing);

        assertThrows(IllegalStateException.class, () -> withFailingMapper.searchLocal("/api/flights"));
    }
}
