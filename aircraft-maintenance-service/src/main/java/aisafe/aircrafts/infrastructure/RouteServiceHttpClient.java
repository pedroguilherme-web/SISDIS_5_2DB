package aisafe.aircrafts.infrastructure;

import aisafe.aircrafts.domain.CompatibleRouteData;
import aisafe.aircrafts.domain.RouteData;
import aisafe.aircrafts.domain.RouteServiceClient;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;
import java.util.List;

@Component
public class RouteServiceHttpClient implements RouteServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RouteServiceHttpClient.class);
    private final RestClient restClient;

    public RouteServiceHttpClient(@Value("${services.routes.url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    private String getAuthorizationHeader() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return request.getHeader(HttpHeaders.AUTHORIZATION);
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public List<CompatibleRouteData> findCompatibleRoutes(double range, int seatCapacity) {
        try {
            var requestSpec = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/routes/compatible")
                            .queryParam("range", range)
                            .queryParam("capacity", seatCapacity)
                            .build());

            String auth = getAuthorizationHeader();
            if (auth != null && !auth.isBlank()) {
                requestSpec.header(HttpHeaders.AUTHORIZATION, auth);
            }

            return requestSpec.retrieve()
                    .body(new ParameterizedTypeReference<List<CompatibleRouteData>>() {});
        } catch (Exception e) {
            log.warn("Failed to retrieve compatible routes from Routes Service: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public RouteData findRouteByOriginAndDestination(String origin, String destination) {
        try {
            var requestSpec = restClient.get()
                    .uri("/api/routes/{origin}/{destination}", origin, destination);

            String auth = getAuthorizationHeader();
            if (auth != null && !auth.isBlank()) {
                requestSpec.header(HttpHeaders.AUTHORIZATION, auth);
            }

            return requestSpec.retrieve()
                    .body(RouteData.class);
        } catch (Exception e) {
            log.warn("Failed to retrieve route between {} and {}: {}", origin, destination, e.getMessage());
            return null;
        }
    }
}
