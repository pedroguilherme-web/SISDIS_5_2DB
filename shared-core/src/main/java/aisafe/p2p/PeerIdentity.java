
package aisafe.p2p;

import java.util.Objects;

public class PeerIdentity {

    private final String peerId;

    public PeerIdentity(String peerId) {
        this.peerId = Objects.requireNonNull(
                peerId, "Peer ID is required"
        );

        if (peerId.isBlank()) {
            throw new IllegalArgumentException(
                    "Peer ID cannot be empty"
            );
        }
    }

    public String getPeerId() {
        return peerId;
    }
}
