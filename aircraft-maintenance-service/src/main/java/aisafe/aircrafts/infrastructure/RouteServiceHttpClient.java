package aisafe.aircrafts.infrastructure;

import aisafe.aircrafts.domain.CompatibleRouteData;
import aisafe.aircrafts.domain.RouteData;
import aisafe.aircrafts.domain.RouteServiceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

@Component
public class RouteServiceHttpClient implements RouteServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RouteServiceHttpClient.class);
    private final RestClient restClient;

    public RouteServiceHttpClient(@Value("${services.routes.url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    @Override
    public List<CompatibleRouteData> findCompatibleRoutes(double range, int seatCapacity) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/routes/compatible")
                            .queryParam("range", range)
                            .queryParam("capacity", seatCapacity)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<CompatibleRouteData>>() {});
        } catch (Exception e) {
            log.warn("Failed to retrieve compatible routes from Routes Service: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public RouteData findRouteByOriginAndDestination(String origin, String destination) {
        try {
            return restClient.get()
                    .uri("/api/routes/by-endpoints?origin={origin}&destination={destination}", origin, destination)
                    .retrieve()
                    .body(RouteData.class);
        } catch (Exception e) {
            log.warn("Failed to retrieve route between {} and {}: {}", origin, destination, e.getMessage());
            return null;
        }
    }
}
