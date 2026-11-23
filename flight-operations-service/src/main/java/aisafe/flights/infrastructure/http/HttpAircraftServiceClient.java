package aisafe.flights.infrastructure.http;

import aisafe.flights.application.dtos.AircraftDTO;
import aisafe.flights.application.ports.AircraftServiceClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class HttpAircraftServiceClient implements AircraftServiceClient {
    static final String SERVICE_NAME = "Aircraft & Maintenance service";

    private final ServiceInstancesClient http;
    private final List<String> instances;

    public HttpAircraftServiceClient(ServiceInstancesClient http,
                                     @Value("${services.aircraft.urls}") List<String> instances) {
        this.http = http;
        this.instances = UrlLists.clean(instances);
    }

    @Override
    public Optional<AircraftDTO> findByRegistration(String registration) {
        return http.get(SERVICE_NAME, instances, "/api/aircrafts/{registration}", registration)
                .map(body -> JsonBodies.read(body, AircraftDTO.class));
    }
}
