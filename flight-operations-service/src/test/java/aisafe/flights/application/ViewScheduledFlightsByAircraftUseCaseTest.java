package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ViewScheduledFlightsByAircraftUseCaseTest {

    private ScheduledFlightRepository repository;
    private RestTemplate restTemplate;
    private ViewScheduledFlightsByAircraftUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ScheduledFlightRepository.class);
        restTemplate = Mockito.mock(RestTemplate.class);
        useCase = new ViewScheduledFlightsByAircraftUseCase(repository, restTemplate);

        ReflectionTestUtils.setField(useCase, "aircraftServiceUrl", "http://localhost:8081");
    }

    @Test
    void shouldThrowExceptionWhenAircraftNotFound() {
        when(restTemplate.getForEntity(anyString(), eq(Void.class)))
                .thenThrow(HttpClientErrorException.NotFound.class);

        assertThrows(RuntimeException.class, () -> useCase.execute("UNKNOWN"));
    }

    @Test
    void shouldReturnFlightsSuccessfully() {
        String aircraftId = "CS-TKA";
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime arrival = departure.plusHours(2);

        ScheduledFlight mockFlight = new ScheduledFlight(1L, departure, arrival, FlightStatus.SCHEDULED, "LIS", "OPO", aircraftId);

        when(restTemplate.getForEntity(anyString(), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());
        when(repository.findByAircraftRegistration(aircraftId))
                .thenReturn(List.of(mockFlight));

        List<FlightResponse> result = useCase.execute(aircraftId);

        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(aircraftId, result.getFirst().aircraftId());
    }
}