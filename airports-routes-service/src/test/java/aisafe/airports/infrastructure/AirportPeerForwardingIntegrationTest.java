package aisafe.airports.infrastructure;

import aisafe.airports.domain.Airport;
import aisafe.airports.domain.AirportRepository;
import aisafe.airports.domain.Runway;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "aisafe.messaging.enabled=false")
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("jpa")
class AirportPeerForwardingIntegrationTest {

    private static final AtomicInteger PEER_REQUESTS = new AtomicInteger();
    private static final AtomicReference<String> FORWARDED_HEADER = new AtomicReference<>();
    private static final HttpServer PEER_SERVER = startPeerServer();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AirportRepository airportRepository;

    @DynamicPropertySource
    static void peerUrl(DynamicPropertyRegistry registry) {
        registry.add("peers.urls", () -> "http://127.0.0.1:"
                + PEER_SERVER.getAddress().getPort());
    }

    @AfterAll
    static void stopPeerServer() {
        PEER_SERVER.stop(0);
    }

    @Test
    void localHitDoesNotCallPeer() throws Exception {
        airportRepository.save(new Airport(
                "LCL",
                "Local Airport",
                "Local City",
                "Portugal",
                "Europe",
                "Europe/Lisbon",
                41.0,
                -8.0,
                List.of(new Runway("01/19", 2500, "010/190"))));
        int requestsBefore = PEER_REQUESTS.get();

        mockMvc.perform(get("/api/v1/airports/LCL"))
                .andExpect(status().isOk());

        assertEquals(requestsBefore, PEER_REQUESTS.get());
    }

    @Test
    void localMissReturnsRemotePeerResultWithLoopPreventionHeader() throws Exception {
        mockMvc.perform(get("/api/v1/airports/REM"))
                .andExpect(status().isOk());

        assertEquals("true", FORWARDED_HEADER.get());
    }

    @Test
    void forwardedMissDoesNotQueryPeerAgain() throws Exception {
        int requestsBefore = PEER_REQUESTS.get();

        mockMvc.perform(get("/api/v1/airports/LOP")
                        .header(AirportPeerForwardingAspect.PEER_FORWARDED_HEADER, "true"))
                .andExpect(status().isNotFound());

        assertEquals(requestsBefore, PEER_REQUESTS.get());
    }

    @Test
    void returnsGlobalNotFoundWhenEveryPeerMisses() throws Exception {
        int requestsBefore = PEER_REQUESTS.get();

        mockMvc.perform(get("/api/v1/airports/MIS"))
                .andExpect(status().isNotFound());

        assertEquals(requestsBefore + 1, PEER_REQUESTS.get());
        assertEquals("true", FORWARDED_HEADER.get());
    }

    private static HttpServer startPeerServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                PEER_REQUESTS.incrementAndGet();
                FORWARDED_HEADER.set(exchange.getRequestHeaders()
                        .getFirst(AirportPeerForwardingAspect.PEER_FORWARDED_HEADER));
                if (exchange.getRequestURI().getPath().endsWith("/REM")) {
                    byte[] body = "{\"peer\":\"remote\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set(
                            HttpHeaders.CONTENT_TYPE,
                            "application/json");
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
