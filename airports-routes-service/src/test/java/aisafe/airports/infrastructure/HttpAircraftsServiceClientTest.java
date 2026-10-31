package aisafe.airports.infrastructure;

import aisafe.airports.domain.ModelName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;

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
        client = new HttpAircraftsServiceClient(restTemplate,
                List.of("http://aircrafts-1", "http://aircrafts-2", "http://aircrafts-3", "http://aircrafts-4"));
    }

    @Test
    void returnsTrueWhenTheAircraftModelExists() {
        server.expect(requestTo("http://aircrafts-1/api/aircraftModels/A320"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertTrue(client.existsByModelName(new ModelName("A320")));
        server.verify();
    }

    @Test
    void returnsFalseWhenTheAircraftModelDoesNotExist() {
        server.expect(requestTo("http://aircrafts-1/api/aircraftModels/A320"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertFalse(client.existsByModelName(new ModelName("A320")));
        server.verify();
    }

    @Test
    void propagatesAircraftServiceNonTransientFailures() {
        server.expect(requestTo("http://aircrafts-1/api/aircraftModels/A320"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThrows(Exception.class,
                () -> client.existsByModelName(new ModelName("A320")));
        server.verify();
    }

    @Test
    void fallsOverToSecondInstanceWhenFirstIsUnreachable() {
        // First instance: connection refused (ResourceAccessException)
        server.expect(requestTo("http://aircrafts-1/api/aircraftModels/A320"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        // Second instance: responds successfully
        server.expect(requestTo("http://aircrafts-2/api/aircraftModels/A320"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertTrue(client.existsByModelName(new ModelName("A320")));
        server.verify();
    }

    @Test
    void fallsOverToThirdInstanceWhenFirstTwoAreUnreachable() {
        server.expect(requestTo("http://aircrafts-1/api/aircraftModels/A320"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://aircrafts-2/api/aircraftModels/A320"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://aircrafts-3/api/aircraftModels/A320"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertTrue(client.existsByModelName(new ModelName("A320")));
        server.verify();
    }

    @Test
    void fallsOverToFourthInstanceWhenFirstThreeAreUnreachable() {
        server.expect(requestTo("http://aircrafts-1/api/aircraftModels/A320"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://aircrafts-2/api/aircraftModels/A320"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://aircrafts-3/api/aircraftModels/A320"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://aircrafts-4/api/aircraftModels/A320"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertTrue(client.existsByModelName(new ModelName("A320")));
        server.verify();
    }

    @Test
    void throwsIllegalStateWhenAllInstancesAreUnreachable() {
        server.expect(requestTo("http://aircrafts-1/api/aircraftModels/A320"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://aircrafts-2/api/aircraftModels/A320"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://aircrafts-3/api/aircraftModels/A320"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        server.expect(requestTo("http://aircrafts-4/api/aircraftModels/A320"))
                .andRespond(request -> { throw new org.springframework.web.client.ResourceAccessException("Connection refused"); });

        assertThrows(IllegalStateException.class,
                () -> client.existsByModelName(new ModelName("A320")));
        server.verify();
    }
}
