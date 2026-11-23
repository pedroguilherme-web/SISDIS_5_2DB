package aisafe.flights.infrastructure.http;

import aisafe.flights.application.ExternalServiceUnavailableException;
import aisafe.flights.application.RemoteRequestRejectedException;
import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.dtos.FlightTimelineResponse;
import aisafe.flights.application.dtos.FlightUtilizationResponse;
import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.flights.application.dtos.TopModelResponse;
import aisafe.flights.application.ports.FlightPeerClient;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.infrastructure.PeerForwarding;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Slf4j
@Component
public class HttpFlightPeerClient implements FlightPeerClient {
    static final String SERVICE_NAME = "Flight Operations peer";

    public static final String INITIAL_STATUS_HEADER = "X-Initial-Status";

    static final int FULL_PAGE = 2000;

    private final RestTemplate restTemplate;
    private final List<String> peerUrls;

    public HttpFlightPeerClient(RestTemplate restTemplate, @Value("${peers.urls:}") List<String> peerUrls) {
        this.restTemplate = restTemplate;
        this.peerUrls = UrlLists.clean(peerUrls);
    }

    @Override
    public List<FlightResponse> findFlightsByAircraft(String registration) {
        return collectLists(path("/api/flights").queryParam("aircraftId", registration), FlightResponse.class);
    }

    @Override
    public boolean anyPeerHasOverlappingFlight(String registration, OffsetDateTime departure, OffsetDateTime arrival) {
        return askAll(path("/api/flights/aircraft/{registration}/conflicts")
                        .queryParam("departure", iso(departure))
                        .queryParam("arrival", iso(arrival)),
                Boolean::parseBoolean, registration)
                .contains(true);
    }

    @Override
    public Optional<FlightResponse> changeStatus(String flightId, FlightStatus newStatus) {
        HttpHeaders headers = peerHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"status\":\"" + newStatus.name() + "\"}", headers);

        boolean somePeerUnreachable = false;
        for (String peer : peerUrls) {
            URI uri = UriComponentsBuilder.fromUriString(peer).path("/api/flights/{id}/status")
                    .buildAndExpand(flightId).encode().toUri();
            try {
                ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.PATCH, entity, String.class);
                return Optional.of(JsonBodies.read(response.getBody(), FlightResponse.class));
            } catch (HttpClientErrorException.NotFound e) {
                log.debug("Peer {} does not own flight {}", peer, flightId);
            } catch (HttpClientErrorException e) {
                throw new RemoteRequestRejectedException(SERVICE_NAME, e.getStatusCode().value(),
                        JsonBodies.errorMessage(e.getResponseBodyAsString()));
            } catch (RestClientException e) {
                somePeerUnreachable = true;
                log.warn("Peer {} unreachable while changing status of flight {}: {}", peer, flightId, e.getMessage());
            }
        }
        if (somePeerUnreachable) {
            throw new ExternalServiceUnavailableException(SERVICE_NAME);
        }
        return Optional.empty();
    }

    @Override
    public FlightResponse scheduleOn(String ownerUrl, ScheduleFlightRequest request, FlightStatus initialStatus) {
        HttpHeaders headers = peerHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(INITIAL_STATUS_HEADER, initialStatus.name());
        URI uri = UriComponentsBuilder.fromUriString(ownerUrl).path("/api/flights").build().toUri();
        try {
            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST,
                    new HttpEntity<>(JsonBodies.write(request), headers), String.class);
            return JsonBodies.read(response.getBody(), FlightResponse.class);
        } catch (HttpClientErrorException e) {
            throw new RemoteRequestRejectedException("Owner replica " + ownerUrl, e.getStatusCode().value(),
                    JsonBodies.errorMessage(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            log.warn("Owner replica {} unreachable for aircraft {}: {}", ownerUrl, request.aircraftId(), e.getMessage());
            throw new ExternalServiceUnavailableException("Owner replica " + ownerUrl + " of aircraft " + request.aircraftId());
        }
    }

    @Override
    public double sumTotalHours(String registration) {
        return askAll(path("/api/flights/aircraft/{registration}/hours"), Double::parseDouble, registration)
                .stream().mapToDouble(Double::doubleValue).sum();
    }

    @Override
    public double sumHoursSince(String registration, OffsetDateTime since) {
        return askAll(path("/api/flights/aircraft/{registration}/hours-since").queryParam("since", iso(since)),
                Double::parseDouble, registration)
                .stream().mapToDouble(Double::doubleValue).sum();
    }

    @Override
    public boolean anyPeerHasFlights(String registration) {
        return askAll(path("/api/flights/aircraft/{registration}/exists"), Boolean::parseBoolean, registration)
                .contains(true);
    }

    @Override
    public long sumCountByRoute(String origin, String destination) {
        return askAll(path("/api/flights/count").queryParam("origin", origin).queryParam("destination", destination),
                body -> Long.parseLong(body.trim()))
                .stream().mapToLong(Long::longValue).sum();
    }

    @Override
    public List<TopModelResponse> modelUtilizationByHours() {
        return collectLists(path("/api/flights/top-models/hours"), TopModelResponse.class);
    }

    @Override
    public List<TopModelResponse> modelUtilizationByAssignments() {
        return collectLists(path("/api/flights/top-models/assignments"), TopModelResponse.class);
    }

    @Override
    public List<FlightTimelineResponse> timeline(String registration) {
        return collectLists(path("/api/flights/aircraft/{registration}/timeline"), FlightTimelineResponse.class, registration);
    }

    @Override
    public List<FlightUtilizationResponse> routeUtilization(OffsetDateTime startDate, OffsetDateTime endDate) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        if (startDate != null) query.add("startDate", iso(startDate));
        if (endDate != null) query.add("endDate", iso(endDate));
        query.add("page", "0");
        query.add("size", String.valueOf(FULL_PAGE));
        return collectLists(path("/api/flights/reports/utilization").queryParams(query), FlightUtilizationResponse.class);
    }

    private <T> List<T> collectLists(UriComponentsBuilder pathBuilder, Class<T> itemType, Object... uriVariables) {
        List<T> all = new ArrayList<>();
        askAll(pathBuilder, body -> JsonBodies.readList(body, itemType), uriVariables).forEach(all::addAll);
        return all;
    }

    private <T> List<T> askAll(UriComponentsBuilder pathBuilder, Function<String, T> parser, Object... uriVariables) {
        HttpEntity<Void> entity = new HttpEntity<>(peerHeaders());
        List<T> answers = new ArrayList<>();
        for (String peer : peerUrls) {
            URI uri = UriComponentsBuilder.fromUriString(peer)
                    .uriComponents(pathBuilder.build())
                    .buildAndExpand(uriVariables)
                    .encode()
                    .toUri();
            try {
                ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
                String body = response.getBody();
                if (body != null && !body.isBlank()) {
                    answers.add(parser.apply(body));
                }
            } catch (RestClientException | IllegalStateException | NumberFormatException e) {
                log.warn("Peer {} skipped for {}: {}", peer, uri.getPath(), e.getMessage());
            }
        }
        return answers;
    }

    private static UriComponentsBuilder path(String path) {
        return UriComponentsBuilder.newInstance().path(path);
    }

    private static HttpHeaders peerHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(PeerForwarding.HEADER, "true");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.parseMediaType("application/hal+json")));
        return headers;
    }

    // UTC with 'Z': a '+' offset in a query string would be decoded as a space.
    static String iso(OffsetDateTime dateTime) {
        return dateTime.withOffsetSameInstant(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
