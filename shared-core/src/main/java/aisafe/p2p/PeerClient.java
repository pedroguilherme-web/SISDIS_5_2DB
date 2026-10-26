
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
}
