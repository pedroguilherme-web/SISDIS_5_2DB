package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@UseCase(readOnly = true)
@RequiredArgsConstructor
public class ViewScheduledFlightsByAircraftUseCase {

    private final ScheduledFlightRepository scheduledFlightRepository;
    private final RestTemplate restTemplate;

    @Value("${services.aircraft.url:http://localhost:8081}")
    private String aircraftServiceUrl;

    public List<FlightResponse> execute(String aircraftId) {
        String normalizedAircraftId = aircraftId.trim().toUpperCase();

        try {
            String aircraftUrl = aircraftServiceUrl + "/api/aircrafts/" + normalizedAircraftId;
            restTemplate.getForEntity(aircraftUrl, Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("Aircraft not found: " + normalizedAircraftId);
        }

        return scheduledFlightRepository.findByAircraftRegistration(normalizedAircraftId).stream()
                .map(FlightResponse::from)
                .toList();
    }
}