package aisafe.routes.infrastructure;

import aisafe.airports.domain.IataCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

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
        client = new HttpFlightsServiceClient(restTemplate,
                List.of("http://flights-1", "http://flights-2", "http://flights-3", "http://flights-4"));
    }

    @Test
    void returnsFlightCountForTheRoute() {
        server.expect(requestTo("http://flights-1/api/flights/count?origin=LIS&destination=OPO"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("3", MediaType.APPLICATION_JSON));

        assertEquals(3, client.countFlightsByRoute(new IataCode("LIS"), new IataCode("OPO")));
        server.verify();
    }

    @Test
    void doesNotTurnFlightsServiceFailuresIntoAnEmptyCount() {
        server.expect(requestTo("http://flights-1/api/flights/count?origin=LIS&destination=OPO"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(Exception.class,
                () -> client.countFlightsByRoute(new IataCode("LIS"), new IataCode("OPO")));
        server.verify();
    }

    @Test
    void rejectsAnEmptySuccessfulResponse() {
        server.expect(requestTo("http://flights-1/api/flights/count?origin=LIS&destination=OPO"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        assertThrows(IllegalStateException.class,
                () -> client.countFlightsByRoute(new IataCode("LIS"), new IataCode("OPO")));
        server.verify();
    }

    @Test
    void fallsOverToSecondInstanceWhenFirstIsUnreachable() {
        server.expect(requestTo("http://flights-1/api/flights/count?origin=LIS&destination=OPO"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://flights-2/api/flights/count?origin=LIS&destination=OPO"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("5", MediaType.APPLICATION_JSON));

        assertEquals(5, client.countFlightsByRoute(new IataCode("LIS"), new IataCode("OPO")));
        server.verify();
    }

    @Test
    void fallsOverToThirdInstanceWhenFirstTwoAreUnreachable() {
        server.expect(requestTo("http://flights-1/api/flights/count?origin=LIS&destination=OPO"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://flights-2/api/flights/count?origin=LIS&destination=OPO"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://flights-3/api/flights/count?origin=LIS&destination=OPO"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("7", MediaType.APPLICATION_JSON));

        assertEquals(7, client.countFlightsByRoute(new IataCode("LIS"), new IataCode("OPO")));
        server.verify();
    }

    @Test
    void fallsOverToFourthInstanceWhenFirstThreeAreUnreachable() {
        server.expect(requestTo("http://flights-1/api/flights/count?origin=LIS&destination=OPO"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://flights-2/api/flights/count?origin=LIS&destination=OPO"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://flights-3/api/flights/count?origin=LIS&destination=OPO"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://flights-4/api/flights/count?origin=LIS&destination=OPO"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("2", MediaType.APPLICATION_JSON));

        assertEquals(2, client.countFlightsByRoute(new IataCode("LIS"), new IataCode("OPO")));
        server.verify();
    }

    @Test
    void throwsIllegalStateWhenAllInstancesAreUnreachable() {
        server.expect(requestTo("http://flights-1/api/flights/count?origin=LIS&destination=OPO"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://flights-2/api/flights/count?origin=LIS&destination=OPO"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://flights-3/api/flights/count?origin=LIS&destination=OPO"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://flights-4/api/flights/count?origin=LIS&destination=OPO"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        assertThrows(IllegalStateException.class,
                () -> client.countFlightsByRoute(new IataCode("LIS"), new IataCode("OPO")));
        server.verify();
    }
}
