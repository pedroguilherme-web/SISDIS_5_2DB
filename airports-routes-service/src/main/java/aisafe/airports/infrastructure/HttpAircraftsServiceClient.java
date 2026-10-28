package aisafe.airports.infrastructure;

import aisafe.airports.application.AircraftsServiceClient;
import aisafe.airports.domain.ModelName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
public class HttpAircraftsServiceClient implements AircraftsServiceClient {

    private final RestTemplate restTemplate;
    private final String aircraftsServiceUrl;

    public HttpAircraftsServiceClient(RestTemplate restTemplate,
                                      @Value("${services.aircrafts.url:http://localhost:8081}") String aircraftsServiceUrl) {
        this.restTemplate = restTemplate;
        this.aircraftsServiceUrl = aircraftsServiceUrl;
    }

    @Override
    public boolean existsByModelName(ModelName modelName) {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    aircraftsServiceUrl + "/api/aircraftModels/{name}",
                    String.class,
                    modelName.getName()
            );
            return response.getStatusCode().is2xxSuccessful();
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        }
    }
}
