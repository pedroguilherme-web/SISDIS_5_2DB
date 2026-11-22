package aisafe.flights.infrastructure.http;

import aisafe.flights.application.ExternalServiceUnavailableException;
import aisafe.flights.application.RemoteRequestRejectedException;
import aisafe.flights.application.dtos.AircraftDTO;
import aisafe.flights.application.dtos.RouteDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ExternalServiceClientsTest {
    private static final MediaType HAL = MediaType.parseMediaType("application/hal+json");
    private static final String AIRCRAFT_HAL = """
            {"registrationNumber":"CS-TRP","model":"A320","manufacturer":"AIRBUS","status":"AVAILABLE",
             "seatCapacity":180,"range":6100.0,"features":[],"version":0,
             "_links":{"self":{"href":"http://a1/api/aircrafts/CS-TRP"}}}
            """;

    private MockRestServiceServer server;
    private HttpAircraftServiceClient aircraftClient;
    private HttpRouteServiceClient routeClient;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        ServiceInstancesClient http = new ServiceInstancesClient(restTemplate);
        aircraftClient = new HttpAircraftServiceClient(http, List.of("http://a1", " http://a2/ "));
        routeClient = new HttpRouteServiceClient(http, List.of("http://r1", "http://r2"));
    }

    @Test
    void shouldReadAircraftFromAHalResponse() {
        server.expect(requestTo("http://a1/api/aircrafts/CS-TRP")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(AIRCRAFT_HAL, HAL));

        AircraftDTO aircraft = aircraftClient.findByRegistration("CS-TRP").orElseThrow();

        assertEquals("A320", aircraft.model());
        assertEquals("AVAILABLE", aircraft.status());
        assertEquals(180, aircraft.seatCapacity());
        assertEquals(6100.0, aircraft.range());
        server.verify();
    }

    @Test
    void shouldFailOverToTheNextInstanceWhenTheFirstIsDown() {
        server.expect(requestTo("http://a1/api/aircrafts/CS-TRP")).andRespond(withException(new ConnectException("refused")));
        server.expect(requestTo("http://a2/api/aircrafts/CS-TRP")).andRespond(withSuccess(AIRCRAFT_HAL, HAL));

        assertTrue(aircraftClient.findByRegistration("CS-TRP").isPresent());
        server.verify();
    }

    @Test
    void shouldFailOverWhenAnInstanceAnswers5xx() {
        server.expect(requestTo("http://a1/api/aircrafts/CS-TRP")).andRespond(withServiceUnavailable());
        server.expect(requestTo("http://a2/api/aircrafts/CS-TRP")).andRespond(withSuccess(AIRCRAFT_HAL, HAL));

        assertTrue(aircraftClient.findByRegistration("CS-TRP").isPresent());
        server.verify();
    }

    @Test
    void shouldTreat404AsADefiniteAnswerAndNotAskOtherInstances() {
        server.expect(requestTo("http://a1/api/aircrafts/CS-XXX")).andRespond(withResourceNotFound());

        assertEquals(Optional.empty(), aircraftClient.findByRegistration("CS-XXX"));
        server.verify();
    }

    @Test
    void shouldPropagateOtherClientErrorsWithTheirStatus() {
        server.expect(requestTo("http://a1/api/aircrafts/CS-TRP"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"message\":\"You do not have permission to access this resource.\"}"));

        RemoteRequestRejectedException ex = assertThrows(RemoteRequestRejectedException.class,
                () -> aircraftClient.findByRegistration("CS-TRP"));
        assertEquals(403, ex.getStatus());
        assertTrue(ex.getMessage().contains("You do not have permission"));
    }

    @Test
    void shouldReportUnavailableWhenEveryInstanceFails() {
        server.expect(requestTo("http://a1/api/aircrafts/CS-TRP")).andRespond(withException(new ConnectException("refused")));
        server.expect(requestTo("http://a2/api/aircrafts/CS-TRP")).andRespond(withServerError());

        ExternalServiceUnavailableException ex = assertThrows(ExternalServiceUnavailableException.class,
                () -> aircraftClient.findByRegistration("CS-TRP"));
        assertEquals(HttpAircraftServiceClient.SERVICE_NAME, ex.getServiceName());
    }

    @Test
    void shouldReadRouteFromTheDirectEndpoint() {
        server.expect(requestTo("http://r1/api/routes/LIS/OPO"))
                .andRespond(withSuccess("""
                        {"originIataCode":"LIS","destinationIataCode":"OPO","estimatedFlightTime":55,
                         "minimumRange":450.5,"minimumCapacity":120,"status":"ACTIVE",
                         "_links":{"self":{"href":"http://r1/api/routes/LIS/OPO"}}}
                        """, HAL));

        RouteDTO route = routeClient.findRoute("LIS", "OPO").orElseThrow();

        assertEquals(120, route.minimumCapacity());
        assertEquals(450.5, route.minimumRange());
        assertTrue(route.isActive());
    }

    @Test
    void shouldReturnEmptyForAnUnknownRoute() {
        server.expect(requestTo("http://r1/api/routes/LIS/XXX")).andRespond(withResourceNotFound());

        assertTrue(routeClient.findRoute("LIS", "XXX").isEmpty());
    }
}
