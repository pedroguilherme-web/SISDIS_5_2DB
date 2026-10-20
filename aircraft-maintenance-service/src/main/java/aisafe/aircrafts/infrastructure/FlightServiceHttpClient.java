package aisafe.aircrafts.infrastructure;

import aisafe.aircrafts.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Component
public class FlightServiceHttpClient implements FlightServiceClient {

    private static final Logger log = LoggerFactory.getLogger(FlightServiceHttpClient.class);
    private final RestClient restClient;

    public FlightServiceHttpClient(@Value("${services.flights.url}") String baseUrl) {
        this.restClient = RestClient.create(baseUrl);
    }

    @Override
    public Double calculateTotalOperationalHoursByRegistration(RegistrationNumber registrationNumber) {
        try {
            return restClient.get()
                    .uri("/api/flights/aircraft/{reg}/hours", registrationNumber.getNumber())
                    .retrieve()
                    .body(Double.class);
        } catch (Exception e) {
            log.warn("Failed to retrieve total operational hours for aircraft {}: {}", registrationNumber.getNumber(), e.getMessage());
            return 0.0;
        }
    }

    @Override
    public Double calculateOperationalHoursSince(RegistrationNumber registrationNumber, LocalDateTime since) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/flights/aircraft/{reg}/hours-since")
                            .queryParam("since", since.toString())
                            .build(registrationNumber.getNumber()))
                    .retrieve()
                    .body(Double.class);
        } catch (Exception e) {
            log.warn("Failed to retrieve operational hours since {} for aircraft {}: {}", since, registrationNumber.getNumber(), e.getMessage());
            return 0.0;
        }
    }

    @Override
    public boolean existsByAircraftRegistration(RegistrationNumber registrationNumber) {
        try {
            Boolean exists = restClient.get()
                    .uri("/api/flights/aircraft/{reg}/exists", registrationNumber.getNumber())
                    .retrieve()
                    .body(Boolean.class);
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("Failed to check if flights exist for aircraft {}: {}", registrationNumber.getNumber(), e.getMessage());
            return false;
        }
    }

    @Override
    public List<TopModelFlightData> findTopModelsByFlightHours(int limit) {
        try {
            return restClient.get()
                    .uri("/api/flights/top-models/hours?limit={limit}", limit)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<TopModelFlightData>>() {});
        } catch (Exception e) {
            log.warn("Failed to retrieve top models by flight hours: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<TopModelFlightData> findTopModelsByAssignments(int limit) {
        try {
            return restClient.get()
                    .uri("/api/flights/top-models/assignments?limit={limit}", limit)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<TopModelFlightData>>() {});
        } catch (Exception e) {
            log.warn("Failed to retrieve top models by assignments: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<FlightTimelineData> findFlightTimelineByRegistration(RegistrationNumber registrationNumber) {
        try {
            return restClient.get()
                    .uri("/api/flights/aircraft/{reg}/timeline", registrationNumber.getNumber())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<FlightTimelineData>>() {});
        } catch (Exception e) {
            log.warn("Failed to retrieve flight timeline for aircraft {}: {}", registrationNumber.getNumber(), e.getMessage());
            return Collections.emptyList();
        }
    }
}
