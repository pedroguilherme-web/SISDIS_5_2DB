package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.domain.ResourceNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewScheduledFlightsByAircraftUseCaseAdditionalTest {

    @Mock
    private ScheduledFlightRepository scheduledFlightRepository;

    @Mock
    private RestTemplate restTemplate;

    private ViewScheduledFlightsByAircraftUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ViewScheduledFlightsByAircraftUseCase(scheduledFlightRepository, restTemplate);
        ReflectionTestUtils.setField(useCase, "aircraftServiceUrl", "http://localhost:8081");
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ensureForwardsAuthHeaderAndReturnsFlights() {
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer token-123");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(servletRequest));

        OffsetDateTime now = OffsetDateTime.now();
        ScheduledFlight flight = new ScheduledFlight(1L, now, now.plusHours(2), FlightStatus.SCHEDULED, "OPO", "LIS", "CS-TPA");

        when(restTemplate.exchange(eq("http://localhost:8081/api/aircrafts/CS-TPA"), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenReturn(ResponseEntity.ok().build());
        when(scheduledFlightRepository.findByAircraftRegistration("CS-TPA")).thenReturn(List.of(flight));

        List<FlightResponse> result = useCase.execute(" cs-tpa ");

        assertEquals(1, result.size());
        ArgumentCaptor<HttpEntity<Void>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(eq("http://localhost:8081/api/aircrafts/CS-TPA"), eq(HttpMethod.GET), captor.capture(), eq(Void.class));
        assertEquals("Bearer token-123", captor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    void ensureThrowsResourceNotFoundExceptionWhenAircraftServiceReturns404() {
        HttpClientErrorException notFound = HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(notFound);

        assertThrows(ResourceNotFoundException.class, () -> useCase.execute("CS-ZZZ"));
        verifyNoInteractions(scheduledFlightRepository);
    }

    @Test
    void ensureContinuesToQueryRepositoryWhenAircraftServiceReturns403Forbidden() {
        HttpClientErrorException forbidden = HttpClientErrorException.create(
                HttpStatus.FORBIDDEN, "Forbidden", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);

        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Void.class)))
                .thenThrow(forbidden);
        when(scheduledFlightRepository.findByAircraftRegistration("CS-TPA")).thenReturn(List.of());

        List<FlightResponse> result = useCase.execute("CS-TPA");

        assertTrue(result.isEmpty());
        verify(scheduledFlightRepository).findByAircraftRegistration("CS-TPA");
    }
}