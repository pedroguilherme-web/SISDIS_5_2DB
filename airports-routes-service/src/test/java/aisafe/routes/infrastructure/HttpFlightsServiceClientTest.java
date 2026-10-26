package aisafe.routes.infrastructure;

import aisafe.airports.domain.IataCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpFlightsServiceClientTest {

    private MockRestServiceServer server;
    private HttpFlightsServiceClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new HttpFlightsServiceClient(restTemplate, "http://flights");
    }

    @Test
    void returnsFlightCountForTheRoute() {
        server.expect(requestTo("http://flights/api/flights/count?origin=LIS&destination=OPO"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("3", MediaType.APPLICATION_JSON));

        assertEquals(3, client.countFlightsByRoute(new IataCode("LIS"), new IataCode("OPO")));
        server.verify();
    }

    @Test
    void doesNotTurnFlightsServiceFailuresIntoAnEmptyCount() {
        server.expect(requestTo("http://flights/api/flights/count?origin=LIS&destination=OPO"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(HttpServerErrorException.class,
                () -> client.countFlightsByRoute(new IataCode("LIS"), new IataCode("OPO")));
        server.verify();
    }

    @Test
    void rejectsAnEmptySuccessfulResponse() {
        server.expect(requestTo("http://flights/api/flights/count?origin=LIS&destination=OPO"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThrows(IllegalStateException.class,
                () -> client.countFlightsByRoute(new IataCode("LIS"), new IataCode("OPO")));
        server.verify();
    }
}
