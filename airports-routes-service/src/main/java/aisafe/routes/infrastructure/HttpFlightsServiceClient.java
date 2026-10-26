package aisafe.routes.infrastructure;

import aisafe.airports.domain.IataCode;
import aisafe.routes.application.FlightsServiceClient;
import org.springframework.beans.factory.annotation.Value;
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
        Long count = restTemplate.getForObject(
                flightsServiceUrl + "/api/flights/count?origin={origin}&destination={destination}",
                Long.class,
                origin.getCode(),
                destination.getCode()
        );
        if (count == null) {
            throw new IllegalStateException("Flights service returned an empty route-count response");
        }
        return count;
    }
}
