package aisafe.shared.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;
import java.util.List;

/**
 * Aspect implementing Peer-to-Peer HTTP GET forwarding for distributed replicas.
 * Taught in LETI-SIDIS Week 3 (PL3) and Week 5.
 * 
 * If an entity query misses the local database (throwing a NotFoundException),
 * this aspect intercepts the call and forwards a unicast HTTP GET to configured peers.
 * Prevents circular forwarding using the 'X-Peer-Forwarded' HTTP header.
 */
@Aspect
@Component
public class PeerForwardingAspect {

    private static final Logger log = LoggerFactory.getLogger(PeerForwardingAspect.class);
    public static final String PEER_FORWARDED_HEADER = "X-Peer-Forwarded";

    private final List<String> peerUrls;
    private final RestTemplate restTemplate;

    public PeerForwardingAspect() {
        this("");
    }

    @org.springframework.beans.factory.annotation.Autowired
    public PeerForwardingAspect(@Value("${peers.urls:${peer.url:}}") String peersConfig) {
        if (peersConfig == null || peersConfig.trim().isEmpty()) {
            this.peerUrls = List.of();
        } else {
            this.peerUrls = Arrays.stream(peersConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3000);
        requestFactory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(requestFactory);
    }

    public PeerForwardingAspect(String peersConfig, RestTemplate restTemplate) {
        if (peersConfig == null || peersConfig.trim().isEmpty()) {
            this.peerUrls = List.of();
        } else {
            this.peerUrls = Arrays.stream(peersConfig.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
        }
        this.restTemplate = restTemplate;
    }

    @Around("@annotation(org.springframework.web.bind.annotation.GetMapping) && within(@org.springframework.web.bind.annotation.RestController *)")
    public Object handleGetForwarding(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            // 1. Try local execution first
            return joinPoint.proceed();
        } catch (Throwable ex) {
            // 2. Check if the thrown exception indicates local resource not found
            if (!isNotFoundException(ex)) {
                throw ex;
            }

            // 3. Obtain the current HTTP servlet request
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes == null) {
                throw ex;
            }
            HttpServletRequest request = attributes.getRequest();

            // 4. Circular loop prevention (check if already forwarded)
            String forwardedHeader = request.getHeader(PEER_FORWARDED_HEADER);
            if ("true".equalsIgnoreCase(forwardedHeader)) {
                log.debug("Request was already forwarded by a peer. Aborting loop propagation.");
                throw ex;
            }

            // 5. If no peers configured, rethrow
            if (peerUrls.isEmpty()) {
                throw ex;
            }

            String requestUri = request.getRequestURI();
            String queryString = request.getQueryString();
            String pathAndQuery = (queryString != null && !queryString.isBlank())
                    ? requestUri + "?" + queryString
                    : requestUri;

            // 6. Preserve authentication header
            HttpHeaders headers = new HttpHeaders();
            headers.set(PEER_FORWARDED_HEADER, "true");
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && !authHeader.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, authHeader);
            }
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            // 7. Iterate through configured peer URLs (Unicast forwarding)
            for (String peerBaseUrl : peerUrls) {
                String targetUrl = peerBaseUrl + pathAndQuery;
                log.info("Local miss. Forwarding HTTP GET unicast to peer: {}", targetUrl);
                try {
                    ResponseEntity<String> response = restTemplate.exchange(
                            targetUrl,
                            HttpMethod.GET,
                            entity,
                            String.class
                    );

                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        log.info("Peer {} successfully resolved the query with status {}", peerBaseUrl, response.getStatusCode());
                        return ResponseEntity.status(response.getStatusCode())
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(response.getBody());
                    }
                } catch (HttpClientErrorException.NotFound e) {
                    log.debug("Peer {} did not find the resource (404).", peerBaseUrl);
                } catch (ResourceAccessException e) {
                    log.warn("Peer {} is unreachable or timed out: {}", peerBaseUrl, e.getMessage());
                } catch (RestClientException e) {
                    log.warn("Failed to retrieve data from peer {}: {}", peerBaseUrl, e.getMessage());
                }
            }

            // If all peers fail to provide the resource, propagate the original exception
            throw ex;
        }
    }

    private boolean isNotFoundException(Throwable ex) {
        String name = ex.getClass().getSimpleName();
        return name.contains("NotFoundException") || name.contains("EntityNotFoundException");
    }

    public List<String> getPeerUrls() {
        return peerUrls;
    }
}
