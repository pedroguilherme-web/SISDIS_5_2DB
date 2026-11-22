package aisafe.flights.infrastructure.http;

import aisafe.flights.application.ExternalServiceUnavailableException;
import aisafe.flights.application.RemoteRequestRejectedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class ServiceInstancesClient {
    private final RestTemplate restTemplate;

    public ServiceInstancesClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public Optional<String> get(String serviceName, List<String> instances, String pathTemplate, Object... uriVariables) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON, MediaType.parseMediaType("application/hal+json")));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        for (String instance : instances) {
            URI uri = UriComponentsBuilder.fromUriString(instance)
                    .path(pathTemplate)
                    .buildAndExpand(uriVariables)
                    .encode()
                    .toUri();
            try {
                ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
                String body = response.getBody();
                return body == null || body.isBlank() ? Optional.empty() : Optional.of(body);
            } catch (HttpClientErrorException.NotFound e) {
                return Optional.empty();
            } catch (HttpClientErrorException e) {
                throw new RemoteRequestRejectedException(serviceName, e.getStatusCode().value(),
                        JsonBodies.errorMessage(e.getResponseBodyAsString()));
            } catch (HttpServerErrorException | ResourceAccessException e) {
                log.warn("{} instance {} failed ({}); trying the next instance", serviceName, instance, e.getMessage());
            }
        }
        throw new ExternalServiceUnavailableException(serviceName);
    }
}
