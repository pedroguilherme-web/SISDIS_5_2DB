package aisafe.shared.infrastructure;

import aisafe.aircrafts.domain.AircraftNotFoundException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeerForwardingAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private RestTemplate restTemplate;

    private PeerForwardingAspect aspect;
    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        aspect = new PeerForwardingAspect("http://localhost:8082", restTemplate);

        mockRequest = new MockHttpServletRequest();
        mockRequest.setRequestURI("/api/aircrafts/CS-TRP");
        mockRequest.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-jwt-token");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
    }

    @Test
    void whenLocalHit_shouldReturnLocalResultWithoutForwarding() throws Throwable {
        String expectedResult = "Local Aircraft Result";
        when(joinPoint.proceed()).thenReturn(expectedResult);

        Object actualResult = aspect.handleGetForwarding(joinPoint);

        assertEquals(expectedResult, actualResult);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void whenLocalMissAndPeerHit_shouldForwardAndReturnPeerResponse() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new AircraftNotFoundException("Not found locally"));

        String peerJson = "{\"registrationNumber\":\"CS-TRP\",\"status\":\"AVAILABLE\"}";
        ResponseEntity<String> peerResponse = ResponseEntity.ok(peerJson);

        when(restTemplate.exchange(
                eq("http://localhost:8082/api/aircrafts/CS-TRP"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(peerResponse);

        Object result = aspect.handleGetForwarding(joinPoint);

        assertNotNull(result);
        assertTrue(result instanceof ResponseEntity<?>);
        ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(peerJson, responseEntity.getBody());
    }

    @Test
    void whenAlreadyForwarded_shouldNotForwardAgainToPreventLoops() throws Throwable {
        mockRequest.addHeader("X-Peer-Forwarded", "true");
        when(joinPoint.proceed()).thenThrow(new AircraftNotFoundException("Not found"));

        assertThrows(AircraftNotFoundException.class, () -> aspect.handleGetForwarding(joinPoint));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void whenPeerUnreachable_shouldCatchExceptionAndRethrowOriginalNotFound() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new AircraftNotFoundException("Not found locally"));

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new ResourceAccessException("Connection refused"));

        assertThrows(AircraftNotFoundException.class, () -> aspect.handleGetForwarding(joinPoint));
    }
}
