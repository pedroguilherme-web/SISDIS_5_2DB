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
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@UseCase
@RequiredArgsConstructor
public class ScheduleFlightUseCase {

    private final ScheduledFlightRepository scheduledFlightRepository;
    private final RestTemplate restTemplate;

    @Value("${services.routes.url:http://localhost:8092}")
    private String routesServiceUrl;

    @Value("${services.aircraft.url:http://localhost:8081}")
    private String aircraftServiceUrl;

    private HttpEntity<Void> createAuthEntity() {
        HttpHeaders headers = new HttpHeaders();
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest currentRequest = attrs.getRequest();
            String authHeader = currentRequest.getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null) {
                headers.set(HttpHeaders.AUTHORIZATION, authHeader);
            }
        }
        return new HttpEntity<>(headers);
    }

    public FlightResponse execute(ScheduleFlightRequest request) {
        String aircraftId = request.aircraftId().trim().toUpperCase();
        String origin = request.originIataCode().trim().toUpperCase();
        String destination = request.destinationIataCode().trim().toUpperCase();

        HttpEntity<Void> entity = createAuthEntity();

        RouteDTO route;
        try {
            String routeUrl = routesServiceUrl + "/api/routes/search?origin=" + origin + "&destination=" + destination;
            ResponseEntity<RouteDTO> routeResponse = restTemplate.exchange(routeUrl, HttpMethod.GET, entity, RouteDTO.class);
            route = routeResponse.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("Route not found: " + origin + " -> " + destination);
        }

        AircraftDTO aircraft;
        try {
            String aircraftUrl = aircraftServiceUrl + "/api/aircrafts/" + aircraftId;
            ResponseEntity<AircraftDTO> aircraftResponse = restTemplate.exchange(aircraftUrl, HttpMethod.GET, entity, AircraftDTO.class);
            aircraft = aircraftResponse.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("Aircraft not found: " + aircraftId);
        }

        if (aircraft != null && ("UNDER_MAINTENANCE".equals(aircraft.status()) || "INACTIVE".equals(aircraft.status()))) {
            throw new AircraftUnavailableException(aircraftId);
        }
        if (aircraft != null && route != null && aircraft.seatCapacity() < route.minimumCapacity()) {
            throw new AircraftIncompatibilityException("Aircraft capacity is less than the route minimum capacity.");
        }
        if (aircraft != null && route != null && aircraft.range() < route.minimumRange()) {
            throw new AircraftIncompatibilityException("Aircraft range is less than the route minimum range.");
        }

        if (scheduledFlightRepository.existsByOverlappingSchedule(aircraftId, request.departureDateTime(), request.arrivalDateTime())) {
            throw new AircraftUnavailableException(aircraftId);
        }

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