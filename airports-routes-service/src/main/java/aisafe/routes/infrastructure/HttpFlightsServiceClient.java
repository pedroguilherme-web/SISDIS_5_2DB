package aisafe.routes.infrastructure;

import aisafe.airports.domain.IataCode;
import aisafe.routes.application.FlightsServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class HttpFlightsServiceClient implements FlightsServiceClient {

    private static final Logger log = LoggerFactory.getLogger(HttpFlightsServiceClient.class);

    private final RestTemplate restTemplate;
    private final List<String> instanceUrls;

    public HttpFlightsServiceClient(RestTemplate restTemplate,
                                    @Value("${services.flights.urls:http://localhost:8083}") List<String> instanceUrls) {
        this.restTemplate = restTemplate;
        this.instanceUrls = instanceUrls;
    }

    @Override
    public long countFlightsByRoute(IataCode origin, IataCode destination) {
        Exception lastFailure = null;
        for (String baseUrl : instanceUrls) {
            try {
                Long count = restTemplate.getForObject(
                        baseUrl + "/api/flights/count?origin={origin}&destination={destination}",
                        Long.class,
                        origin.getCode(),
                        destination.getCode()
                );
                if (count == null) {
                    throw new IllegalStateException("Flights service returned an empty route-count response");
                }
                return count;
            } catch (ResourceAccessException e) {
                log.warn("Flights service instance unreachable: {} — trying next instance", baseUrl);
                lastFailure = e;
            }
        }
        throw new IllegalStateException("All flights service instances are unavailable", lastFailure);
    }
}
