package aisafe.airports.infrastructure;

import aisafe.airports.domain.AirportNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

@Aspect
@Component
public class AirportPeerForwardingAspect {

    public static final String PEER_FORWARDED_HEADER = "X-Peer-Forwarded";
    private static final Logger log =
            LoggerFactory.getLogger(AirportPeerForwardingAspect.class);

    private final List<String> peerUrls;
    private final RestTemplate restTemplate;

    public AirportPeerForwardingAspect() {
        this("", createRestTemplate());
    }

    @Autowired
    public AirportPeerForwardingAspect(
            @Value("${peers.urls:}") String peersConfig) {
        this(peersConfig, createRestTemplate());
    }

    AirportPeerForwardingAspect(
            String peersConfig,
            RestTemplate restTemplate) {
        this.peerUrls = peersConfig == null || peersConfig.isBlank()
                ? List.of()
                : Arrays.stream(peersConfig.split(","))
                        .map(String::trim)
                        .filter(value -> !value.isBlank())
                        .toList();
        this.restTemplate = restTemplate;
    }

    @Around("@annotation(org.springframework.web.bind.annotation.GetMapping) "
            + "&& within(aisafe.airports.infrastructure..*)")
    public Object forwardOnLocalMiss(ProceedingJoinPoint joinPoint)
            throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (AirportNotFoundException exception) {
            ServletRequestAttributes attributes =
                    (ServletRequestAttributes) RequestContextHolder
                            .getRequestAttributes();

            if (attributes == null
                    || peerUrls.isEmpty()
                    || alreadyForwarded(attributes.getRequest())) {
                throw exception;
            }

            Object peerResponse = queryPeers(
                    attributes.getRequest(),
                    exception
            );
            if (peerResponse != null) {
                return peerResponse;
            }
            throw exception;
        }
    }

    private boolean alreadyForwarded(HttpServletRequest request) {
        return "true".equalsIgnoreCase(
                request.getHeader(PEER_FORWARDED_HEADER)
        );
    }

    private Object queryPeers(
            HttpServletRequest request,
            AirportNotFoundException originalException) {
        String pathAndQuery = request.getRequestURI()
                + (request.getQueryString() == null
                        || request.getQueryString().isBlank()
                        ? ""
                        : "?" + request.getQueryString());

        HttpHeaders headers = new HttpHeaders();
        headers.set(PEER_FORWARDED_HEADER, "true");
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        String authorization =
                request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && !authorization.isBlank()) {
            headers.set(HttpHeaders.AUTHORIZATION, authorization);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        for (String peerUrl : peerUrls) {
            String targetUrl = peerUrl + pathAndQuery;
            try {
                ResponseEntity<Object> response = restTemplate.exchange(
                        targetUrl,
                        HttpMethod.GET,
                        entity,
                        Object.class
                );

                if (response.getStatusCode().is2xxSuccessful()
                        && response.getBody() != null) {
                    return response;
                }
            } catch (HttpClientErrorException.NotFound exception) {
                log.debug("Peer {} has no airport for {}", peerUrl, pathAndQuery);
            } catch (ResourceAccessException exception) {
                log.warn("Peer {} unreachable or timed out", peerUrl);
            } catch (RestClientException exception) {
                log.warn("Peer {} returned an invalid response", peerUrl);
            }
        }

        log.debug("No peer resolved local miss: {}", originalException.getMessage());
        return null;
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory =
                new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(2));
        return new RestTemplate(factory);
    }

    List<String> getPeerUrls() {
        return peerUrls;
    }
}
