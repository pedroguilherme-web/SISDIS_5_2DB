package aisafe.flights.infrastructure.http;

import aisafe.flights.application.dtos.RouteDTO;
import aisafe.flights.application.ports.RouteServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class HttpRouteServiceClient implements RouteServiceClient {
    static final String SERVICE_NAME = "Airports & Routes service";

    private final ServiceInstancesClient http;
    private final List<String> instances;

    public HttpRouteServiceClient(ServiceInstancesClient http,
                                  @Value("${services.routes.urls}") List<String> instances) {
        this.http = http;
        this.instances = UrlLists.clean(instances);
    }

    @Override
    public Optional<RouteDTO> findRoute(String originIataCode, String destinationIataCode) {
        return http.get(SERVICE_NAME, instances, "/api/routes/{origin}/{destination}", originIataCode, destinationIataCode)
                .map(body -> JsonBodies.read(body, RouteDTO.class));
    }
}
