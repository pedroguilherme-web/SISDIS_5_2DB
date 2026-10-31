
package aisafe.p2p;

import java.net.URI;
import java.util.Objects;

public class PeerNode {

    private final String id;
    private final URI baseUrl;

    public PeerNode(String id, URI baseUrl) {
        this.id = Objects.requireNonNull(id, "Peer ID is required");
        this.baseUrl = Objects.requireNonNull(baseUrl, "Peer URL is required");

        if (!"http".equalsIgnoreCase(baseUrl.getScheme())
                && !"https".equalsIgnoreCase(baseUrl.getScheme())) {
            throw new IllegalArgumentException("Peer URL must use HTTP or HTTPS");
        }
    }

    public String getId() {
        return id;
    }

    public URI getBaseUrl() {
        return baseUrl;
    }
}
