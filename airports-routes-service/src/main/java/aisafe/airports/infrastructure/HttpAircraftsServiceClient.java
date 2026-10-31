package aisafe.airports.infrastructure;

import aisafe.airports.application.AircraftsServiceClient;
import aisafe.airports.domain.ModelName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class HttpAircraftsServiceClient implements AircraftsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpAircraftsServiceClient.class);

    private final RestTemplate restTemplate;
    private final List<String> instanceUrls;

    public HttpAircraftsServiceClient(RestTemplate restTemplate,
                                      @Value("${services.aircrafts.urls:http://localhost:8081}") List<String> instanceUrls) {
        this.restTemplate = restTemplate;
        this.instanceUrls = instanceUrls;
    }

    @Override
    public boolean existsByModelName(ModelName modelName) {
        Exception lastFailure = null;
        for (String baseUrl : instanceUrls) {
            try {
                ResponseEntity<String> response = restTemplate.getForEntity(
                        baseUrl + "/api/aircraftModels/{name}",
                        String.class,
                        modelName.getName()
                );
                return response.getStatusCode().is2xxSuccessful();
            } catch (HttpClientErrorException.NotFound e) {
                return false;
            } catch (ResourceAccessException e) {
                log.warn("Aircrafts service instance unreachable: {} — trying next instance", baseUrl);
                lastFailure = e;
            }
        }
        throw new IllegalStateException("All aircrafts service instances are unavailable", lastFailure);
    }
}
