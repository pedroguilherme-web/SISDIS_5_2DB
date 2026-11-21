package aisafe.airports.infrastructure;

import aisafe.airports.domain.AirportNotFoundException;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AirportPeerForwardingAspectTest {

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private RestTemplate restTemplate;

    private AirportPeerForwardingAspect aspect;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        aspect = new AirportPeerForwardingAspect(
                "http://localhost:8093",
                restTemplate
        );
        request = new MockHttpServletRequest(
                "GET",
                "/api/airports/LIS"
        );
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer test-token");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request)
        );
    }

    @Test
    void localHitDoesNotForward() throws Throwable {
        when(joinPoint.proceed()).thenReturn("local result");

        assertEquals("local result", aspect.forwardOnLocalMiss(joinPoint));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void localMissForwardsOnceAndReturnsPeerResult() throws Throwable {
        when(joinPoint.proceed())
                .thenThrow(new AirportNotFoundException("LIS"));
        when(restTemplate.exchange(
                eq("http://localhost:8093/api/airports/LIS"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenReturn(ResponseEntity.ok().body("peer result"));

        Object result = aspect.forwardOnLocalMiss(joinPoint);

        assertEquals(HttpStatus.OK,
                ((ResponseEntity<?>) result).getStatusCode());
        assertEquals("peer result",
                ((ResponseEntity<?>) result).getBody());
    }

    @Test
    void alreadyForwardedRequestDoesNotLoop() throws Throwable {
        request.addHeader(
                AirportPeerForwardingAspect.PEER_FORWARDED_HEADER,
                "true"
        );
        when(joinPoint.proceed())
                .thenThrow(new AirportNotFoundException("LIS"));

        assertThrows(
                AirportNotFoundException.class,
                () -> aspect.forwardOnLocalMiss(joinPoint)
        );
        verifyNoInteractions(restTemplate);
    }

    @Test
    void peer404ThenPeerOfflineRethrowsOriginalMiss() throws Throwable {
        when(joinPoint.proceed())
                .thenThrow(new AirportNotFoundException("LIS"));
        when(restTemplate.exchange(
                eq("http://localhost:8093/api/airports/LIS"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Object.class)
        ))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND,
                        "Not found",
                        HttpHeaders.EMPTY,
                        new byte[0],
                        null
                ));

        assertThrows(
                AirportNotFoundException.class,
                () -> aspect.forwardOnLocalMiss(joinPoint)
        );
    }

    @Test
    void unreachablePeerRethrowsOriginalMiss() throws Throwable {
        when(joinPoint.proceed())
                .thenThrow(new AirportNotFoundException("LIS"));
        when(restTemplate.exchange(
                eq("http://localhost:8093/api/airports/LIS"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Object.class)
        ))
                .thenThrow(new ResourceAccessException("offline"));

        assertThrows(
                AirportNotFoundException.class,
                () -> aspect.forwardOnLocalMiss(joinPoint)
        );
    }
}
