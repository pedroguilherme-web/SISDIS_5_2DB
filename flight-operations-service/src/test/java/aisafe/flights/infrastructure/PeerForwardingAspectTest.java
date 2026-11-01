package aisafe.flights.infrastructure;

import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeerForwardingAspectTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ProceedingJoinPoint joinPoint;

    private PeerForwardingAspect aspect;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        aspect = new PeerForwardingAspect(restTemplate);
        ReflectionTestUtils.setField(aspect, "peerUrls", List.of("http://localhost:8072"));

        request = new MockHttpServletRequest("GET", "/api/flights/aircraft/CS-TPA");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void ensureProceedsDirectlyWhenRequestAttributesAreNull() throws Throwable {
        RequestContextHolder.resetRequestAttributes();
        when(joinPoint.proceed()).thenReturn("localResult");

        Object result = aspect.forwardOnLocalMiss(joinPoint);

        assertEquals("localResult", result);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void ensureReturnsLocalResultWhenNotEmpty() throws Throwable {
        ResponseEntity<List<String>> localResponse = ResponseEntity.ok(List.of("Flight1"));
        when(joinPoint.proceed()).thenReturn(localResponse);

        Object result = aspect.forwardOnLocalMiss(joinPoint);

        assertEquals(localResponse, result);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void ensureForwardsToPeerWhenLocalCollectionResponseIsEmpty() throws Throwable {
        ResponseEntity<List<String>> emptyLocal = ResponseEntity.ok(Collections.emptyList());
        ResponseEntity<Object> peerResponse = ResponseEntity.ok(List.of("PeerFlight1"));

        request.setQueryString("page=0&size=10");
        request.addHeader("Authorization", "Bearer test-token");

        when(joinPoint.proceed()).thenReturn(emptyLocal);
        when(restTemplate.exchange(
                eq("http://localhost:8072/api/flights/aircraft/CS-TPA?page=0&size=10"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenReturn(peerResponse);

        Object result = aspect.forwardOnLocalMiss(joinPoint);

        assertEquals(peerResponse, result);

        ArgumentCaptor<HttpEntity<Void>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(anyString(), eq(HttpMethod.GET), entityCaptor.capture(), eq(Object.class));
        assertEquals("true", entityCaptor.getValue().getHeaders().getFirst("X-Peer-Forwarded"));
        assertEquals("Bearer test-token", entityCaptor.getValue().getHeaders().getFirst("Authorization"));
    }

    @Test
    void ensureDoesNotForwardWhenAlreadyForwardedAndLocalIsEmpty() throws Throwable {
        request.addHeader("X-Peer-Forwarded", "true");
        List<String> emptyList = Collections.emptyList();
        when(joinPoint.proceed()).thenReturn(emptyList);

        Object result = aspect.forwardOnLocalMiss(joinPoint);

        assertEquals(emptyList, result);
        verifyNoInteractions(restTemplate);
    }

    @Test
    void ensureForwardsToPeerWhenLocalThrowsRuntimeException() throws Throwable {
        ResponseEntity<Object> peerResponse = ResponseEntity.ok("PeerFlightData");

        when(joinPoint.proceed()).thenThrow(new RuntimeException("Flight not found locally"));
        when(restTemplate.exchange(
                eq("http://localhost:8072/api/flights/aircraft/CS-TPA"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenReturn(peerResponse);

        Object result = aspect.forwardOnLocalMiss(joinPoint);

        assertEquals(peerResponse, result);
    }

    @Test
    void ensureRethrowsExceptionWhenAlreadyForwardedAndLocalThrows() throws Throwable {
        request.addHeader("X-Peer-Forwarded", "true");
        when(joinPoint.proceed()).thenThrow(new IllegalArgumentException("Not found"));

        assertThrows(IllegalArgumentException.class, () -> aspect.forwardOnLocalMiss(joinPoint));
        verifyNoInteractions(restTemplate);
    }

    @Test
    void ensureRethrowsExceptionWhenAllPeersFailOrReturnNull() throws Throwable {
        when(joinPoint.proceed()).thenThrow(new RuntimeException("Local miss"));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenThrow(new RestClientException("Connection refused"));

        assertThrows(RuntimeException.class, () -> aspect.forwardOnLocalMiss(joinPoint));
    }

    @Test
    void ensureReturnsLocalEmptyWhenPeerAlsoReturnsEmptyCollection() throws Throwable {
        List<String> emptyLocal = Collections.emptyList();
        ResponseEntity<Object> emptyPeer = ResponseEntity.ok(Collections.emptyList());

        when(joinPoint.proceed()).thenReturn(emptyLocal);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(Object.class)))
                .thenReturn(emptyPeer);

        Object result = aspect.forwardOnLocalMiss(joinPoint);

        assertEquals(emptyLocal, result);
    }

    @Test
    void ensureTriesSecondPeerWhenFirstPeerIsUnreachable() throws Throwable {
        ReflectionTestUtils.setField(aspect, "peerUrls", List.of(" ", "http://localhost:8072", "http://localhost:8073"));

        List<String> emptyLocal = Collections.emptyList();
        ResponseEntity<Object> secondPeerResponse = new ResponseEntity<>(List.of("FlightFromPeer2"), HttpStatus.OK);

        when(joinPoint.proceed()).thenReturn(emptyLocal);
        when(restTemplate.exchange(
                eq("http://localhost:8072/api/flights/aircraft/CS-TPA"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenThrow(new RestClientException("Peer 1 down"));

        when(restTemplate.exchange(
                eq("http://localhost:8073/api/flights/aircraft/CS-TPA"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Object.class)
        )).thenReturn(secondPeerResponse);

        Object result = aspect.forwardOnLocalMiss(joinPoint);

        assertEquals(secondPeerResponse, result);
    }
}