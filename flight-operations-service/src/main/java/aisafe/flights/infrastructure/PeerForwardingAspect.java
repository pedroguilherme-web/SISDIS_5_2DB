package aisafe.flights.infrastructure;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.List;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PeerForwardingAspect {

    private static final String PEER_HEADER = "X-Peer-Forwarded";

    private final RestTemplate restTemplate;

    @Value("#{'${peers.urls:}'.split(',')}")
    private List<String> peerUrls;

    @Around("@annotation(org.springframework.web.bind.annotation.GetMapping) && within(aisafe.flights.infrastructure..*)")
    public Object forwardOnLocalMiss(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return joinPoint.proceed();
        }

        HttpServletRequest request = attrs.getRequest();
        boolean alreadyForwarded = "true".equalsIgnoreCase(request.getHeader(PEER_HEADER));

        try {
            Object localResult = joinPoint.proceed();

            // Se o resultado local for uma lista vazia (ex: voos de uma aeronave que estão noutro peer)
            // e ainda não for um pedido reencaminhado, tenta obter dos peers
            if (!alreadyForwarded && isEmptyResponse(localResult)) {
                Object peerResponse = queryPeers(request);
                if (peerResponse != null) {
                    return peerResponse;
                }
            }
            return localResult;

        } catch (RuntimeException ex) {
            // Se já veio de um peer (Loop Prevention), não volta a reencaminhar
            if (alreadyForwarded) {
                throw ex;
            }
            // Se falhou localmente (ex: NotFound), tenta procurar nos peers
            Object peerResponse = queryPeers(request);
            if (peerResponse != null) {
                return peerResponse;
            }
            throw ex;
        }
    }

    private boolean isEmptyResponse(Object result) {
        if (result instanceof Collection<?> col) {
            return col.isEmpty();
        }
        if (result instanceof ResponseEntity<?> resp && resp.getBody() instanceof Collection<?> col) {
            return col.isEmpty();
        }
        return false;
    }

    private Object queryPeers(HttpServletRequest request) {
        if (peerUrls == null || peerUrls.isEmpty()) {
            return null;
        }

        String uri = request.getRequestURI();
        String query = request.getQueryString();
        String fullPath = (query != null && !query.isBlank()) ? uri + "?" + query : uri;

        HttpHeaders headers = new HttpHeaders();
        headers.set(PEER_HEADER, "true");
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null) {
            headers.set("Authorization", authHeader);
        }

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        for (String rawPeer : peerUrls) {
            String peer = rawPeer.trim();
            if (peer.isEmpty()) continue;

            String targetUrl = peer + fullPath;
            try {
                log.info("Local miss on {}. Forwarding GET to peer: {}", uri, targetUrl);
                ResponseEntity<Object> response = restTemplate.exchange(
                        targetUrl, HttpMethod.GET, entity, Object.class);

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    if (!(response.getBody() instanceof Collection<?> col && col.isEmpty())) {
                        return response;
                    }
                }
            } catch (RestClientException e) {
                log.warn("Peer {} unreachable or returned error: {}", peer, e.getMessage());
            }
        }
        return null;
    }
}