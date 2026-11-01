package aisafe.flights.application;

import aisafe.flights.application.dtos.AircraftDTO;
import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.dtos.RouteDTO;
import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.flights.domain.AircraftIncompatibilityException;
import aisafe.flights.domain.AircraftUnavailableException;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@UseCase
@RequiredArgsConstructor
public class ScheduleFlightUseCase {

    private final ScheduledFlightRepository scheduledFlightRepository;
    private final RestTemplate restTemplate;

    @Value("${services.routes.url:http://localhost:8091}")
    private String routesServiceUrl;

    @Value("${services.aircraft.url:http://localhost:8081}")
    private String aircraftServiceUrl;

    public FlightResponse execute(ScheduleFlightRequest request) {
        String aircraftId = request.aircraftId().trim().toUpperCase();
        String origin = request.originIataCode().trim().toUpperCase();
        String destination = request.destinationIataCode().trim().toUpperCase();

        // 1. Pedido HTTP ao Airports & Routes Service (presumindo porta 8082)
        RouteDTO route;
        try {
            String routeUrl = routesServiceUrl + "/api/routes/search?origin=" + origin + "&destination=" + destination;
            route = restTemplate.getForObject(routeUrl, RouteDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("Route not found: " + origin + " -> " + destination);
        }

        // 2. Pedido HTTP ao Aircraft & Maintenance Service (presumindo porta 8081)
        AircraftDTO aircraft;
        try {
            String aircraftUrl = aircraftServiceUrl + "/api/aircrafts/" + aircraftId;
            aircraft = restTemplate.getForObject(aircraftUrl, AircraftDTO.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("Aircraft not found: " + aircraftId);
        }

        // 3. Validações de Regras de Negócio (usando os dados recebidos via DTOs)
        if (aircraft != null && ("UNDER_MAINTENANCE".equals(aircraft.status()) || "INACTIVE".equals(aircraft.status()))) {
            throw new AircraftUnavailableException(aircraftId);
        }
        if (aircraft != null && route != null && aircraft.seatCapacity() < route.minimumCapacity()) {
            throw new AircraftIncompatibilityException("Aircraft capacity is less than the route minimum capacity.");
        }
        if (aircraft != null && route != null && aircraft.range() < route.minimumRange()) {
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