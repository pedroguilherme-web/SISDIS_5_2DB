package aisafe.routes.infrastructure;

import aisafe.airports.domain.IataCode;
import aisafe.routes.application.FlightsServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class HttpFlightsServiceClient implements FlightsServiceClient {

    private final RestTemplate restTemplate;
    private final String flightsServiceUrl;

    public HttpFlightsServiceClient(RestTemplate restTemplate,
                                    @Value("${services.flights.url:http://localhost:8083}") String flightsServiceUrl) {
        this.restTemplate = restTemplate;
        this.flightsServiceUrl = flightsServiceUrl;
    }

    @Override
    public long countFlightsByRoute(IataCode origin, IataCode destination) {
        try {
            ResponseEntity<Long> response = restTemplate.getForEntity(
                    flightsServiceUrl + "/api/flights/count?origin={origin}&destination={destination}",
                    Long.class,
                    origin.getCode(),
                    destination.getCode()
            );
            return response.getBody() != null ? response.getBody() : 0L;
        } catch (Exception e) {
            // Se o serviço falhar, assumimos 0 de popularidade temporariamente (Disponibilidade > Consistência forte)
            return 0L;
        }
    }
}
