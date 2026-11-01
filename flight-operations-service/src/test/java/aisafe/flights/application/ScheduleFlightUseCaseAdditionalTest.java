package aisafe.flights.application;

import aisafe.flights.application.dtos.AircraftDTO;
import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.dtos.RouteDTO;
import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.flights.domain.AircraftIncompatibilityException;
import aisafe.flights.domain.AircraftUnavailableException;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.domain.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleFlightUseCaseAdditionalTest {

    @Mock
    private ScheduledFlightRepository scheduledFlightRepository;

    @Mock
    private RestTemplate restTemplate;

    @Captor
    private ArgumentCaptor<HttpEntity<Void>> entityCaptor;

    private ScheduleFlightUseCase useCase;
    private OffsetDateTime departure;
    private OffsetDateTime arrival;
    private ScheduleFlightRequest validRequest;

    @BeforeEach
    void setUp() {
        useCase = new ScheduleFlightUseCase(scheduledFlightRepository, restTemplate);
        ReflectionTestUtils.setField(useCase, "routesServiceUrl", "http://localhost:8091");
        ReflectionTestUtils.setField(useCase, "aircraftServiceUrl", "http://localhost:8081");

        departure = OffsetDateTime.now().plusDays(1);
        arrival = departure.plusHours(2);
        validRequest = new ScheduleFlightRequest("CS-TPA", "OPO", "LIS", departure, arrival);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ensureForwardsAuthorizationHeaderWhenRequestContextIsPresent() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer my-jwt-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

        RouteDTO route = new RouteDTO(100, 300);
        AircraftDTO aircraft = new AircraftDTO("ACTIVE", 180, 5000);
        ScheduledFlight savedFlight = new ScheduledFlight(1L, departure, arrival, FlightStatus.SCHEDULED, "OPO", "LIS", "CS-TPA");

        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(RouteDTO.class)))
                .thenReturn(ResponseEntity.ok(route));
        when(restTemplate.exchange(contains("/api/aircrafts/CS-TPA"), eq(HttpMethod.GET), any(HttpEntity.class), eq(AircraftDTO.class)))
                .thenReturn(ResponseEntity.ok(aircraft));
        when(scheduledFlightRepository.existsByOverlappingSchedule(anyString(), any(), any())).thenReturn(false);
        when(scheduledFlightRepository.save(any(ScheduledFlight.class))).thenReturn(savedFlight);

        FlightResponse response = useCase.execute(validRequest);

        assertNotNull(response);
        verify(restTemplate).exchange(contains("/api/routes/search"), eq(HttpMethod.GET), entityCaptor.capture(), eq(RouteDTO.class));
        assertEquals("Bearer my-jwt-token", entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void ensureFallbackExtractsRouteFromJsonArrayWhenDirectDtoExtractionFails() {
        String jsonArray = "[{\"minimumCapacity\":120,\"minimumRange\":450.5,\"status\":\"ACTIVE\"}]";
        AircraftDTO aircraft = new AircraftDTO("ACTIVE", 180, 5000);
        ScheduledFlight savedFlight = new ScheduledFlight(1L, departure, arrival, FlightStatus.SCHEDULED, "OPO", "LIS", "CS-TPA");

        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(RouteDTO.class)))
                .thenThrow(new RestClientException("Cannot deserialize array to RouteDTO"));
        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(jsonArray));
        when(restTemplate.exchange(contains("/api/aircrafts/CS-TPA"), eq(HttpMethod.GET), any(HttpEntity.class), eq(AircraftDTO.class)))
                .thenReturn(ResponseEntity.ok(aircraft));
        when(scheduledFlightRepository.existsByOverlappingSchedule(anyString(), any(), any())).thenReturn(false);
        when(scheduledFlightRepository.save(any(ScheduledFlight.class))).thenReturn(savedFlight);

        FlightResponse response = useCase.execute(validRequest);

        assertNotNull(response);
        assertEquals(1L, response.id());
    }

    @Test
    void ensureFallbackExtractsRouteFromPagedDataFieldWithDistanceFallback() {
        String pagedJson = "{\"data\":[{\"minimumCapacity\":100,\"distance\":600.0}]}";
        AircraftDTO aircraft = new AircraftDTO("ACTIVE", 180, 5000);
        ScheduledFlight savedFlight = new ScheduledFlight(1L, departure, arrival, FlightStatus.SCHEDULED, "OPO", "LIS", "CS-TPA");

        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(RouteDTO.class)))
                .thenThrow(new RestClientException("Cannot deserialize"));
        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(pagedJson));
        when(restTemplate.exchange(contains("/api/aircrafts/CS-TPA"), eq(HttpMethod.GET), any(HttpEntity.class), eq(AircraftDTO.class)))
                .thenReturn(ResponseEntity.ok(aircraft));
        when(scheduledFlightRepository.existsByOverlappingSchedule(anyString(), any(), any())).thenReturn(false);
        when(scheduledFlightRepository.save(any(ScheduledFlight.class))).thenReturn(savedFlight);

        FlightResponse response = useCase.execute(validRequest);

        assertNotNull(response);
    }

    @Test
    void ensureFallbackExtractsRouteFromEmbeddedHateoasField() {
        String hateoasJson = "{\"_embedded\":{\"routeList\":[{\"minimumCapacity\":100,\"minimumRange\":300}]}}";
        AircraftDTO aircraft = new AircraftDTO("ACTIVE", 180, 5000);
        ScheduledFlight savedFlight = new ScheduledFlight(1L, departure, arrival, FlightStatus.SCHEDULED, "OPO", "LIS", "CS-TPA");

        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(RouteDTO.class)))
                .thenThrow(new RestClientException("Cannot deserialize"));
        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(hateoasJson));
        when(restTemplate.exchange(contains("/api/aircrafts/CS-TPA"), eq(HttpMethod.GET), any(HttpEntity.class), eq(AircraftDTO.class)))
                .thenReturn(ResponseEntity.ok(aircraft));
        when(scheduledFlightRepository.existsByOverlappingSchedule(anyString(), any(), any())).thenReturn(false);
        when(scheduledFlightRepository.save(any(ScheduledFlight.class))).thenReturn(savedFlight);

        FlightResponse response = useCase.execute(validRequest);

        assertNotNull(response);
    }

    @Test
    void ensureThrowsResourceNotFoundExceptionWhenFallbackJsonArrayIsEmpty() {
        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(RouteDTO.class)))
                .thenThrow(new RestClientException("Cannot deserialize"));
        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("[]"));

        assertThrows(ResourceNotFoundException.class, () -> useCase.execute(validRequest));
    }

    @Test
    void ensureThrowsResourceNotFoundExceptionWhenFallbackBodyIsBlank() {
        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(RouteDTO.class)))
                .thenThrow(new RestClientException("Cannot deserialize"));
        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("   "));

        assertThrows(ResourceNotFoundException.class, () -> useCase.execute(validRequest));
    }

    @Test
    void ensureThrowsIllegalStateExceptionWhenFallbackJsonIsMalformed() {
        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(RouteDTO.class)))
                .thenThrow(new RestClientException("Cannot deserialize"));
        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("{invalid-json"));

        assertThrows(IllegalStateException.class, () -> useCase.execute(validRequest));
    }

    @Test
    void ensureThrowsResourceNotFoundExceptionWhenRouteReturns404() {
        HttpClientErrorException notFound = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(RouteDTO.class)))
                .thenThrow(notFound);

        assertThrows(ResourceNotFoundException.class, () -> useCase.execute(validRequest));
    }

    @Test
    void ensureThrowsResourceNotFoundExceptionWhenAircraftReturns404() {
        RouteDTO route = new RouteDTO(100, 300);
        HttpClientErrorException notFound = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(RouteDTO.class)))
                .thenReturn(ResponseEntity.ok(route));
        when(restTemplate.exchange(contains("/api/aircrafts/CS-TPA"), eq(HttpMethod.GET), any(HttpEntity.class), eq(AircraftDTO.class)))
                .thenThrow(notFound);

        assertThrows(ResourceNotFoundException.class, () -> useCase.execute(validRequest));
    }

    @Test
    void ensureThrowsAircraftIncompatibilityWhenRangeIsInsufficient() {
        RouteDTO route = new RouteDTO(100, 6000);
        AircraftDTO aircraft = new AircraftDTO("ACTIVE", 180, 3000);

        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(RouteDTO.class)))
                .thenReturn(ResponseEntity.ok(route));
        when(restTemplate.exchange(contains("/api/aircrafts/CS-TPA"), eq(HttpMethod.GET), any(HttpEntity.class), eq(AircraftDTO.class)))
                .thenReturn(ResponseEntity.ok(aircraft));

        assertThrows(AircraftIncompatibilityException.class, () -> useCase.execute(validRequest));
    }

    @Test
    void ensureThrowsAircraftUnavailableWhenStatusIsInactive() {
        RouteDTO route = new RouteDTO(100, 500);
        AircraftDTO aircraft = new AircraftDTO("INACTIVE", 180, 5000);

        when(restTemplate.exchange(contains("/api/routes/search"), eq(HttpMethod.GET), any(HttpEntity.class), eq(RouteDTO.class)))
                .thenReturn(ResponseEntity.ok(route));
        when(restTemplate.exchange(contains("/api/aircrafts/CS-TPA"), eq(HttpMethod.GET), any(HttpEntity.class), eq(AircraftDTO.class)))
                .thenReturn(ResponseEntity.ok(aircraft));

        assertThrows(AircraftUnavailableException.class, () -> useCase.execute(validRequest));
    }
}