package aisafe.flights.infrastructure.http;

import aisafe.flights.application.ExternalServiceUnavailableException;
import aisafe.flights.application.RemoteRequestRejectedException;
import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.dtos.FlightUtilizationResponse;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.infrastructure.PeerForwarding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class HttpFlightPeerClientTest {
    private static final MediaType HAL = MediaType.parseMediaType("application/hal+json");
    private static final String FLIGHT_JSON = """
            {"id":"f-9","aircraftId":"CS-TRP","aircraftModel":"A320","originIataCode":"LIS","destinationIataCode":"OPO",
             "departureDateTime":"2026-12-01T08:00:00Z","arrivalDateTime":"2026-12-01T10:00:00Z","status":"%s",
             "_links":{"change-status":{"href":"http://p2/api/flights/f-9/status"}}}
            """;

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private HttpFlightPeerClient client;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        client = new HttpFlightPeerClient(restTemplate, List.of("http://p1", "http://p2"));
    }

    @Test
    void shouldCollectFlightsFromReachablePeersAndSkipTheOnesThatAreDown() {
        server.expect(requestTo("http://p1/api/flights?aircraftId=CS-TRP"))
                .andExpect(header(PeerForwarding.HEADER, "true"))
                .andRespond(withSuccess("{\"_embedded\":{\"flightResponseList\":[" + FLIGHT_JSON.formatted("SCHEDULED") + "]}}", HAL));
        server.expect(requestTo("http://p2/api/flights?aircraftId=CS-TRP"))
                .andRespond(withException(new ConnectException("refused")));

        List<FlightResponse> flights = client.findFlightsByAircraft("CS-TRP");

        assertEquals(1, flights.size());
        assertEquals("f-9", flights.getFirst().id());
        assertEquals(OffsetDateTime.parse("2026-12-01T08:00:00Z"), flights.getFirst().departureDateTime());
        server.verify();
    }

    @Test
    void shouldReadAnEmptyHalCollection() {
        server.expect(requestTo("http://p1/api/flights?aircraftId=CS-TRP"))
                .andRespond(withSuccess("{\"_links\":{\"self\":{\"href\":\"x\"}}}", HAL));
        server.expect(requestTo("http://p2/api/flights?aircraftId=CS-TRP"))
                .andRespond(withSuccess("{\"_links\":{\"self\":{\"href\":\"x\"}}}", HAL));

        assertTrue(client.findFlightsByAircraft("CS-TRP").isEmpty());
    }

    @Test
    void shouldAskEveryPeerAboutConflictsUsingUtcDates() {
        OffsetDateTime departure = OffsetDateTime.of(2026, 12, 1, 9, 0, 0, 0, ZoneOffset.ofHours(1));
        server.expect(requestTo("http://p1/api/flights/aircraft/CS-TRP/conflicts?departure=2026-12-01T08:00:00Z&arrival=2026-12-01T10:00:00Z"))
                .andRespond(withSuccess("false", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://p2/api/flights/aircraft/CS-TRP/conflicts?departure=2026-12-01T08:00:00Z&arrival=2026-12-01T10:00:00Z"))
                .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));

        assertTrue(client.anyPeerHasOverlappingFlight("CS-TRP", departure, departure.plusHours(2)));
        server.verify();
    }

    @Test
    void shouldForwardStatusChangeToThePeerThatOwnsTheFlight() {
        server.expect(requestTo("http://p1/api/flights/f-9/status")).andRespond(withResourceNotFound());
        server.expect(requestTo("http://p2/api/flights/f-9/status"))
                .andExpect(method(HttpMethod.PATCH))
                .andExpect(header(PeerForwarding.HEADER, "true"))
                .andExpect(content().json("{\"status\":\"COMPLETED\"}"))
                .andRespond(withSuccess(FLIGHT_JSON.formatted("COMPLETED"), HAL));

        Optional<FlightResponse> result = client.changeStatus("f-9", FlightStatus.COMPLETED);

        assertEquals(FlightStatus.COMPLETED, result.orElseThrow().status());
        server.verify();
    }

    @Test
    void shouldPassOnTheRejectionOfThePeerThatOwnsTheFlight() {
        server.expect(requestTo("http://p1/api/flights/f-9/status"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Flight cannot change status from COMPLETED to SCHEDULED.\"}"));

        RemoteRequestRejectedException ex = assertThrows(RemoteRequestRejectedException.class,
                () -> client.changeStatus("f-9", FlightStatus.SCHEDULED));
        assertEquals(409, ex.getStatus());
    }

    @Test
    void shouldReturnEmptyWhenNoPeerOwnsTheFlight() {
        server.expect(requestTo("http://p1/api/flights/f-9/status")).andRespond(withResourceNotFound());
        server.expect(requestTo("http://p2/api/flights/f-9/status")).andRespond(withResourceNotFound());

        assertTrue(client.changeStatus("f-9", FlightStatus.CANCELED).isEmpty());
    }

    @Test
    void shouldReportUnavailableInsteadOfNotFoundWhenAPeerCouldNotBeAsked() {
        server.expect(requestTo("http://p1/api/flights/f-9/status")).andRespond(withException(new ConnectException("refused")));
        server.expect(requestTo("http://p2/api/flights/f-9/status")).andRespond(withResourceNotFound());

        assertThrows(ExternalServiceUnavailableException.class, () -> client.changeStatus("f-9", FlightStatus.CANCELED));
    }

    @Test
    void shouldAddHoursOfEveryPeerAndIgnoreUnparseableAnswers() {
        server.expect(requestTo("http://p1/api/flights/aircraft/CS-TRP/hours")).andRespond(withSuccess("2.5", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://p2/api/flights/aircraft/CS-TRP/hours")).andRespond(withSuccess("oops", MediaType.APPLICATION_JSON));

        assertEquals(2.5, client.sumTotalHours("CS-TRP"), 1e-9);
    }

    @Test
    void shouldAddRouteCountsOfEveryPeer() {
        server.expect(requestTo("http://p1/api/flights/count?origin=LIS&destination=OPO")).andRespond(withSuccess("3", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://p2/api/flights/count?origin=LIS&destination=OPO")).andRespond(withSuccess("4", MediaType.APPLICATION_JSON));

        assertEquals(7L, client.sumCountByRoute("LIS", "OPO"));
    }

    @Test
    void shouldFetchTheWholeLocalReportOfEachPeer() {
        String page = """
                {"_embedded":{"flightUtilizationResponseList":[{"origin":"LIS","destination":"OPO","count":5}]},
                 "page":{"size":2000,"totalElements":1,"totalPages":1,"number":0}}
                """;
        server.expect(requestTo("http://p1/api/flights/reports/utilization?page=0&size=2000")).andRespond(withSuccess(page, HAL));
        server.expect(requestTo("http://p2/api/flights/reports/utilization?page=0&size=2000")).andRespond(withServiceUnavailable());

        assertEquals(List.of(new FlightUtilizationResponse("LIS", "OPO", 5)), client.routeUtilization(null, null));
    }

    @Test
    void shouldDoNothingWhenNoPeersAreConfigured() {
        HttpFlightPeerClient alone = new HttpFlightPeerClient(restTemplate, List.of(" ", ""));

        assertTrue(alone.findFlightsByAircraft("CS-TRP").isEmpty());
        assertFalse(alone.anyPeerHasFlights("CS-TRP"));
        assertEquals(Optional.empty(), alone.changeStatus("f-9", FlightStatus.CANCELED));
        server.verify();
    }

    @Test
    void shouldForwardASchedulingCommandToTheOwnerReplica() {
        server.expect(requestTo("http://p2/api/flights"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(PeerForwarding.HEADER, "true"))
                .andExpect(header(HttpFlightPeerClient.INITIAL_STATUS_HEADER, "COMPLETED"))
                .andExpect(jsonPath("$.aircraftId").value("CS-TRP"))
                .andExpect(jsonPath("$.departureDateTime").value("2026-12-01T08:00:00Z"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(HAL).body(FLIGHT_JSON.formatted("COMPLETED")));

        var request = new aisafe.flights.application.dtos.ScheduleFlightRequest("CS-TRP", "LIS", "OPO",
                OffsetDateTime.parse("2026-12-01T08:00:00Z"), OffsetDateTime.parse("2026-12-01T10:00:00Z"));

        FlightResponse response = client.scheduleOn("http://p2", request, FlightStatus.COMPLETED);

        assertEquals("f-9", response.id());
        server.verify();
    }

    @Test
    void shouldPassOnTheOwnersRejectionAndReportAnUnreachableOwnerAs503() {
        var request = new aisafe.flights.application.dtos.ScheduleFlightRequest("CS-TRP", "LIS", "OPO",
                OffsetDateTime.parse("2026-12-01T08:00:00Z"), OffsetDateTime.parse("2026-12-01T10:00:00Z"));
        server.expect(requestTo("http://p2/api/flights"))
                .andRespond(withStatus(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"Aircraft CS-TRP is unavailable in the requested timeframe.\"}"));
        server.expect(requestTo("http://p2/api/flights")).andRespond(withException(new ConnectException("refused")));

        RemoteRequestRejectedException conflict = assertThrows(RemoteRequestRejectedException.class,
                () -> client.scheduleOn("http://p2", request, FlightStatus.SCHEDULED));
        assertEquals(409, conflict.getStatus());
        assertThrows(ExternalServiceUnavailableException.class, () -> client.scheduleOn("http://p2", request, FlightStatus.SCHEDULED));
    }
}
