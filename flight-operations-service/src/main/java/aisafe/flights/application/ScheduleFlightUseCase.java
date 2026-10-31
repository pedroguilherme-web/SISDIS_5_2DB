package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.flights.application.dtos.AircraftDTO; // Importação adicionada
import aisafe.flights.application.dtos.RouteDTO;    // Importação adicionada
import aisafe.flights.domain.AircraftIncompatibilityException;
import aisafe.flights.domain.AircraftUnavailableException;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@UseCase
@RequiredArgsConstructor
public class ScheduleFlightUseCase {

    private final ScheduledFlightRepository scheduledFlightRepository;
    private final RestTemplate restTemplate;

    public FlightResponse execute(ScheduleFlightRequest request) {
        String aircraftId = request.aircraftId().trim().toUpperCase();
        String origin = request.originIataCode().trim().toUpperCase();
        String destination = request.destinationIataCode().trim().toUpperCase();

        // 1. Pedido HTTP ao Airports & Routes Service (presumindo porta 8082)
        RouteDTO route;
        try {
            String routeUrl = "http://localhost:8082/api/routes/search?origin=" + origin + "&destination=" + destination;
            route = restTemplate.getForObject(routeUrl, RouteDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("Route not found: " + origin + " -> " + destination);
        }

        // 2. Pedido HTTP ao Aircraft & Maintenance Service (presumindo porta 8081)
        AircraftDTO aircraft;
        try {
            String aircraftUrl = "http://localhost:8081/api/aircrafts/" + aircraftId;
            aircraft = restTemplate.getForObject(aircraftUrl, AircraftDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("Aircraft not found: " + aircraftId);
        }

        // 3. Validações de Regras de Negócio (usando os dados recebidos via DTOs)
        if ("UNDER_MAINTENANCE".equals(aircraft.status()) || "INACTIVE".equals(aircraft.status())) {
            throw new AircraftUnavailableException(aircraftId);
        }
        if (aircraft.seatCapacity() < route.minimumCapacity()) {
            throw new AircraftIncompatibilityException("Aircraft capacity is less than the route minimum capacity.");
        }
        if (aircraft.range() < route.minimumRange()) {
            throw new AircraftIncompatibilityException("Aircraft range is less than the route minimum range.");
        }

        // 4. Validação de sobreposição de horários (Local - o teu serviço é dono disto)
        if (scheduledFlightRepository.existsByOverlappingSchedule(aircraftId, request.departureDateTime(), request.arrivalDateTime())) {
            throw new AircraftUnavailableException(aircraftId);
        }

        // 5. Guardar o voo (Agora com as Strings corretas para o novo construtor)
        ScheduledFlight scheduledFlight = new ScheduledFlight(
                request.departureDateTime(),
                request.arrivalDateTime(),
                FlightStatus.SCHEDULED,
                origin,
                destination,
                aircraftId
        );

        ScheduledFlight saved = scheduledFlightRepository.save(scheduledFlight);
        return FlightResponse.from(saved);
    }
}