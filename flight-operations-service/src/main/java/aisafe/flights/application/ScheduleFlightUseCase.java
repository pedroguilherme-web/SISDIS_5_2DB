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
import aisafe.shared.domain.ResourceNotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@UseCase
@RequiredArgsConstructor
public class ScheduleFlightUseCase {

    private final ScheduledFlightRepository scheduledFlightRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${services.routes.url:http://localhost:8091}")
    private String routesServiceUrl;

    @Value("${services.aircraft.url:http://localhost:8081}")
    private String aircraftServiceUrl;

    public FlightResponse execute(ScheduleFlightRequest request) {
        String aircraftId = request.aircraftId().trim().toUpperCase();
        String origin = request.originIataCode().trim().toUpperCase();
        String destination = request.destinationIataCode().trim().toUpperCase();

        HttpEntity<Void> entity = buildForwardingEntity();

        RouteDTO route = fetchRoute(origin, destination, entity);
        AircraftDTO aircraft = fetchAircraft(aircraftId, entity);

        validateAircraftAndRoute(aircraftId, aircraft, route, request);

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

    private void validateAircraftAndRoute(
            String aircraftId,
            AircraftDTO aircraft,
            RouteDTO route,
            ScheduleFlightRequest request
    ) {
        if (aircraft != null) {
            if ("UNDER_MAINTENANCE".equals(aircraft.status()) || "INACTIVE".equals(aircraft.status())) {
                throw new AircraftUnavailableException(aircraftId);
            }
            if (route != null) {
                if (aircraft.seatCapacity() < route.minimumCapacity()) {
                    throw new AircraftIncompatibilityException("Aircraft capacity is less than the route minimum capacity.");
                }
                if (aircraft.range() < route.minimumRange()) {
                    throw new AircraftIncompatibilityException("Aircraft range is less than the route minimum range.");
                }
            }
        }

        if (scheduledFlightRepository.existsByOverlappingSchedule(
                aircraftId, request.departureDateTime(), request.arrivalDateTime())) {
            throw new AircraftUnavailableException(aircraftId);
        }
    }

    private AircraftDTO fetchAircraft(String aircraftId, HttpEntity<Void> entity) {
        try {
            String aircraftUrl = aircraftServiceUrl + "/api/aircrafts/" + aircraftId;
            ResponseEntity<AircraftDTO> response = restTemplate.exchange(
                    aircraftUrl, HttpMethod.GET, entity, AircraftDTO.class);
            return response != null ? response.getBody() : null;
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Aircraft not found: " + aircraftId) {};
        }
    }

    private RouteDTO fetchRoute(String origin, String destination, HttpEntity<Void> entity) {
        String directUrl = routesServiceUrl + "/api/routes/" + origin + "/" + destination;
        String searchUrl = routesServiceUrl + "/api/routes/search?origin=" + origin + "&destination=" + destination;

        try {
            ResponseEntity<RouteDTO> routeResponse = restTemplate.exchange(
                    searchUrl, HttpMethod.GET, entity, RouteDTO.class);
            if (routeResponse != null && routeResponse.getBody() != null) {
                return routeResponse.getBody();
            }
        } catch (HttpClientErrorException.NotFound ignored) {
            // Tenta o endpoint direto /api/routes/{origin}/{destination}
        } catch (RestClientException ex) {
            try {
                return fetchRouteFromJsonTree(searchUrl, origin, destination, entity);
            } catch (ResourceNotFoundException ignored) {
                // Tenta o endpoint direto abaixo
            }
        }

        return fetchRouteFromJsonTree(directUrl, origin, destination, entity);
    }

    private RouteDTO fetchRouteFromJsonTree(String routeUrl, String origin, String destination, HttpEntity<Void> entity) {
        try {
            ResponseEntity<String> rawResponse = restTemplate.exchange(
                    routeUrl, HttpMethod.GET, entity, String.class);
            if (rawResponse == null || rawResponse.getBody() == null || rawResponse.getBody().isBlank()) {
                throw new ResourceNotFoundException("Route not found: " + origin + " -> " + destination) {};
            }

            String body = rawResponse.getBody();
            JsonNode root = objectMapper.readTree(body);
            JsonNode routeNode = extractFirstRouteNode(root);
            if (routeNode == null) {
                throw new ResourceNotFoundException("Route not found: " + origin + " -> " + destination) {};
            }

            int minCap = routeNode.has("minimumCapacity") ? routeNode.get("minimumCapacity").asInt(0) : 0;
            int minRange = routeNode.has("minimumRange") ? (int) routeNode.get("minimumRange").asDouble(0.0)
                    : (routeNode.has("distance") ? (int) routeNode.get("distance").asDouble(0.0) : 0);

            return new RouteDTO(minCap, minRange);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("Route not found: " + origin + " -> " + destination) {};
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Route response: " + e.getMessage(), e);
        }
    }

    private JsonNode extractFirstRouteNode(JsonNode root) {
        if (root.isArray()) {
            return root.isEmpty() ? null : root.get(0);
        }
        for (String field : new String[]{"data", "content"}) {
            if (root.has(field) && root.get(field).isArray()) {
                JsonNode arr = root.get(field);
                return arr.isEmpty() ? null : arr.get(0);
            }
        }
        if (root.has("_embedded")) {
            var elements = root.get("_embedded").elements();
            if (elements.hasNext()) {
                JsonNode arr = elements.next();
                if (arr.isArray() && !arr.isEmpty()) {
                    return arr.get(0);
                }
            }
            return null;
        }
        return (root.has("minimumCapacity") || root.has("originAirport") || root.has("origin")) ? root : null;
    }

    private HttpEntity<Void> buildForwardingEntity() {
        HttpHeaders headers = new HttpHeaders();
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest req = attrs.getRequest();
            String auth = req.getHeader(HttpHeaders.AUTHORIZATION);
            if (auth != null) {
                headers.set(HttpHeaders.AUTHORIZATION, auth);
            }
        }
        return new HttpEntity<>(headers);
    }
}