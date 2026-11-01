package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.application.UseCase;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;

@UseCase(readOnly = true)
@RequiredArgsConstructor
public class ViewScheduledFlightsByAircraftUseCase {

    private final ScheduledFlightRepository scheduledFlightRepository;
    private final RestTemplate restTemplate;

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

    public List<FlightResponse> execute(String aircraftId) {
        String normalizedAircraftId = aircraftId.trim().toUpperCase();

        try {
            String aircraftUrl = aircraftServiceUrl + "/api/aircrafts/" + normalizedAircraftId;
            HttpEntity<Void> entity = createAuthEntity();
            restTemplate.exchange(aircraftUrl, HttpMethod.GET, entity, Void.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new RuntimeException("Aircraft not found: " + normalizedAircraftId);
        } catch (HttpClientErrorException.Forbidden e) {
        }

        return scheduledFlightRepository.findByAircraftRegistration(normalizedAircraftId).stream()
                .map(FlightResponse::from)
                .toList();
    }
}