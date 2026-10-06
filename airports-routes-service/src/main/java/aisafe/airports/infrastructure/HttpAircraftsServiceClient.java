package aisafe.airports.infrastructure;

import aisafe.airports.application.AircraftsServiceClient;
import aisafe.airports.domain.ModelName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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
            ResponseEntity<Void> response = restTemplate.getForEntity(
                    aircraftsServiceUrl + "/api/aircraft-models/{name}",
                    Void.class,
                    modelName.getName()
            );
            return response.getStatusCode() == HttpStatus.OK;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (Exception e) {
            // Se o serviço de aeronaves estiver em baixo, assumimos false ou lançamos excepção.
            // Para eventual consistency, poderíamos aceitar temporariamente ou usar circuit breaker.
            return false;
        }
    }
}
