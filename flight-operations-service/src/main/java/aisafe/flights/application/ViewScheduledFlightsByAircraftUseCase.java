package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@UseCase(readOnly = true)
@RequiredArgsConstructor
public class ViewScheduledFlightsByAircraftUseCase {

    private final ScheduledFlightRepository scheduledFlightRepository;
    private final RestTemplate restTemplate;

    public List<FlightResponse> execute(String aircraftId) {
        String normalizedAircraftId = aircraftId.trim().toUpperCase();

        // Verificar via HTTP se a aeronave existe no outro serviço
        try {
            String aircraftUrl = "http://localhost:8081/api/aircrafts/" + normalizedAircraftId;
            restTemplate.getForEntity(aircraftUrl, Void.class); // Só precisamos saber se devolve 200 OK
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("Aircraft not found: " + normalizedAircraftId);
        }

        // Se chegou aqui, a aeronave existe. Vamos buscar os voos à nossa base de dados.
        return scheduledFlightRepository.findByAircraftRegistration(normalizedAircraftId).stream()
                .map(FlightResponse::from)
                .toList();
    }
}