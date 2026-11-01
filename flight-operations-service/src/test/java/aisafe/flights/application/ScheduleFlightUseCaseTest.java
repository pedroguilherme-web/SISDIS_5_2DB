package aisafe.flights.application;

import aisafe.flights.application.dtos.AircraftDTO;
import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.dtos.RouteDTO;
import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.flights.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class ScheduleFlightUseCaseTest {

    private ScheduledFlightRepository repository;
    private RestTemplate restTemplate;
    private ScheduleFlightUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ScheduledFlightRepository.class);
        restTemplate = Mockito.mock(RestTemplate.class);
        useCase = new ScheduleFlightUseCase(repository, restTemplate);

        ReflectionTestUtils.setField(useCase, "routesServiceUrl", "http://localhost:8091");
        ReflectionTestUtils.setField(useCase, "aircraftServiceUrl", "http://localhost:8081");
    }

    @Test
    void shouldThrowExceptionWhenRouteNotFound() {
        ScheduleFlightRequest request = new ScheduleFlightRequest("CS-TKA", "XXX", "YYY", OffsetDateTime.now(), OffsetDateTime.now().plusHours(1));

        when(restTemplate.getForObject(anyString(), eq(RouteDTO.class)))
                .thenThrow(HttpClientErrorException.NotFound.class);

        assertThrows(RuntimeException.class, () -> useCase.execute(request));
    }

    @Test
    void shouldThrowAircraftUnavailableWhenUnderMaintenance() {
        ScheduleFlightRequest request = new ScheduleFlightRequest("CS-TRP", "LIS", "OPO", OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC).plusHours(2));
        RouteDTO route = new RouteDTO(100, 500);
        AircraftDTO aircraft = new AircraftDTO("UNDER_MAINTENANCE", 150, 3000);

        when(restTemplate.getForObject(anyString(), eq(RouteDTO.class))).thenReturn(route);
        when(restTemplate.getForObject(anyString(), eq(AircraftDTO.class))).thenReturn(aircraft);

        assertThrows(AircraftUnavailableException.class, () -> useCase.execute(request));
    }

    @Test
    void shouldThrowIncompatibilityWhenCapacityIsLow() {
        ScheduleFlightRequest request = new ScheduleFlightRequest("CS-TRP", "LIS", "OPO", OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC).plusHours(2));
        RouteDTO route = new RouteDTO(200, 500); // Requer 200 lugares
        AircraftDTO aircraft = new AircraftDTO("AVAILABLE", 150, 3000); // Só tem 150

        when(restTemplate.getForObject(anyString(), eq(RouteDTO.class))).thenReturn(route);
        when(restTemplate.getForObject(anyString(), eq(AircraftDTO.class))).thenReturn(aircraft);

        assertThrows(AircraftIncompatibilityException.class, () -> useCase.execute(request));
    }

    @Test
    void shouldScheduleFlightSuccessfully() {
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime arrival = departure.plusHours(2);
        ScheduleFlightRequest request = new ScheduleFlightRequest("CS-TRP", "LIS", "OPO", departure, arrival);

        RouteDTO route = new RouteDTO(100, 500);
        AircraftDTO aircraft = new AircraftDTO("AVAILABLE", 150, 3000);

        ScheduledFlight savedFlight = new ScheduledFlight(1L, departure, arrival, FlightStatus.SCHEDULED, "LIS", "OPO", "CS-TRP");

        when(restTemplate.getForObject(anyString(), eq(RouteDTO.class))).thenReturn(route);
        when(restTemplate.getForObject(anyString(), eq(AircraftDTO.class))).thenReturn(aircraft);
        when(repository.existsByOverlappingSchedule(anyString(), any(), any())).thenReturn(false);
        when(repository.save(any(ScheduledFlight.class))).thenReturn(savedFlight);

        FlightResponse response = useCase.execute(request);

        assertNotNull(response);
        assertEquals("CS-TRP", response.aircraftId());
        assertEquals(FlightStatus.SCHEDULED, response.status());
    }
}