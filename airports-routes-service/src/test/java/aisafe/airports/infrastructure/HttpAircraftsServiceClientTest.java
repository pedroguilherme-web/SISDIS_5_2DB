package aisafe.airports.infrastructure;

import aisafe.airports.domain.ModelName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HttpAircraftsServiceClientTest {

    private MockRestServiceServer server;
    private HttpAircraftsServiceClient client;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.createServer(restTemplate);
        client = new HttpAircraftsServiceClient(restTemplate, "http://aircrafts");
    }

    @Test
    void returnsTrueWhenTheAircraftModelExists() {
        server.expect(requestTo("http://aircrafts/api/aircraftModels/A320"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertTrue(client.existsByModelName(new ModelName("A320")));
        server.verify();
    }

    @Test
    void returnsFalseWhenTheAircraftModelDoesNotExist() {
        server.expect(requestTo("http://aircrafts/api/aircraftModels/A320"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFalse(client.existsByModelName(new ModelName("A320")));
        server.verify();
    }

    @Test
    void propagatesAircraftServiceFailures() {
        server.expect(requestTo("http://aircrafts/api/aircraftModels/A320"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(HttpServerErrorException.class,
                () -> client.existsByModelName(new ModelName("A320")));
        server.verify();
    }
}
