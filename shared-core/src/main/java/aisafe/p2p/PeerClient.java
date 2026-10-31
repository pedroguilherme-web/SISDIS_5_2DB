
package aisafe.p2p;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public class PeerClient {

    private final RestClient restClient;
    private PeerTokenService peerTokenService;
    private PeerIdentity peerIdentity;

    public PeerClient() {

        this.restClient = RestClient.builder()
                .requestFactory(
                        new org.springframework.http.client
                                .JdkClientHttpRequestFactory(
                                java.net.http.HttpClient.newBuilder()
                                        .connectTimeout(Duration.ofSeconds(3))
                                        .build()
                        )
                )
                .build();
    }

    public PeerClient(
            PeerTokenService peerTokenService,
            PeerIdentity peerIdentity) {

        this();

        this.peerTokenService = Objects.requireNonNull(
                peerTokenService, "Peer token service is required"
        );

        this.peerIdentity = Objects.requireNonNull(
                peerIdentity, "Peer identity is required"
        );
    }


    public String searchPeer(PeerNode peer, String path) {
        Objects.requireNonNull(peer, "Peer is required");
        Objects.requireNonNull(path, "Path is required");

        if (!path.startsWith("/") || path.startsWith("//")) {
            throw new IllegalArgumentException(
                    "Path must start with a single /"
            );
        }

        URI target = peer.getBaseUrl().resolve(path);

        try {
            return restClient
                    .method(HttpMethod.GET)
                    .uri(target)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw new IllegalStateException(
                                "Peer returned HTTP " + response.getStatusCode()
                        );
                    })
                    .body(String.class);

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Unable to communicate with peer: " + peer.getId(),
                    exception
            );
        }
    }

    public String forwardSearch(
            PeerNode peer,
            DistributedSearchRequest searchRequest) {

        Objects.requireNonNull(peer, "Peer is required");
        Objects.requireNonNull(searchRequest, "Search request is required");

        if (searchRequest.getTtl() <= 0) {
            throw new IllegalStateException(
                    "Cannot forward search: TTL exhausted"
            );
        }

        DistributedSearchRequest forwarded =
                searchRequest.nextHop();

        URI target = peer.getBaseUrl()
                .resolve("/api/p2p/search");


        try {
            if (peerTokenService == null || peerIdentity == null) {
                throw new IllegalStateException(
                        "P2P authentication is not configured"
                );
            }

            String token = peerTokenService.generateToken(peerIdentity);

            return restClient
                    .post()
                    .uri(target)
                    .headers(headers -> headers.setBearerAuth(token))
                    .body(forwarded)

                    .retrieve()
                    .onStatus(HttpStatusCode::isError,
                            (request, response) -> {
                                throw new IllegalStateException(
                                        "Peer returned HTTP "
                                                + response.getStatusCode()
                                );
                            })
                    .body(String.class);

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Unable to forward search to peer: "
                            + peer.getId(),
                    exception
            );
        }
    }

}
