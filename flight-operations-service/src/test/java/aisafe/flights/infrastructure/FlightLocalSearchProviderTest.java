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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightLocalSearchProviderTest {

    @Mock
    private ScheduledFlightRepository scheduledFlightRepository;

    private FlightLocalSearchProvider provider;

    @BeforeEach
    void setUp() {
        provider = new FlightLocalSearchProvider(scheduledFlightRepository);
    }

    @Test
    void ensureSearchLocalReturnsEmptyArrayWhenPathIsNull() {
        String result = provider.searchLocal(null);
        assertEquals("[]", result);
        verifyNoInteractions(scheduledFlightRepository);
    }

    @Test
    void ensureSearchLocalReturnsEmptyArrayWhenPathDoesNotStartWithApiFlights() {
        String result = provider.searchLocal("/api/airports/search");
        assertEquals("[]", result);
        verifyNoInteractions(scheduledFlightRepository);
    }

    @Test
    void ensureSearchLocalReturnsSerializedFlightsWhenPathIsValid() {
        OffsetDateTime departure = OffsetDateTime.parse("2026-11-01T10:00:00Z");
        OffsetDateTime arrival = OffsetDateTime.parse("2026-11-01T12:00:00Z");

        ScheduledFlight flight = new ScheduledFlight(
                1L,
                departure,
                arrival,
                FlightStatus.SCHEDULED,
                "OPO",
                "LIS",
                "CS-TPA"
        );

        when(scheduledFlightRepository.findAll()).thenReturn(List.of(flight));

        String json = provider.searchLocal("/api/flights/aircraft/CS-TPA");

        assertNotNull(json);
        assertTrue(json.contains("\"id\":1"));
        assertTrue(json.contains("OPO"));
        assertTrue(json.contains("LIS"));
        assertTrue(json.contains("CS-TPA"));
        assertTrue(json.contains("SCHEDULED"));
        verify(scheduledFlightRepository, times(1)).findAll();
    }

    @Test
    void ensureSearchLocalReturnsEmptyJsonArrayWhenNoFlightsExist() {
        when(scheduledFlightRepository.findAll()).thenReturn(List.of());

        String json = provider.searchLocal("/api/flights");

        assertEquals("[]", json);
        verify(scheduledFlightRepository, times(1)).findAll();
    }

    @Test
    void ensureSearchLocalThrowsIllegalStateExceptionOnJsonProcessingException() throws JsonProcessingException {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        FlightLocalSearchProvider providerWithMockMapper =
                new FlightLocalSearchProvider(scheduledFlightRepository, mockMapper);

        when(scheduledFlightRepository.findAll()).thenReturn(List.of());
        when(mockMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("Simulated JSON failure") {});

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> providerWithMockMapper.searchLocal("/api/flights")
        );

        assertEquals("Failed to serialize local flight results", ex.getMessage());
    }
}