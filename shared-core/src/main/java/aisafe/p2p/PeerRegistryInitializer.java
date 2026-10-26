
package aisafe.p2p;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class PeerRegistryInitializer {

    public PeerRegistryInitializer(
            PeerRegistry registry,
            PeerIdentity identity,
            @Value("${p2p.peers:}") String configuredPeers
    ) {
        if (configuredPeers == null || configuredPeers.isBlank()) {
            return;
        }

        for (String entry : configuredPeers.split(",")) {
            String peerEntry = entry.trim();

            if (peerEntry.isEmpty()) {
                continue;
            }

            String[] parts = peerEntry.split("=", 2);

            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "Invalid peer configuration: " + peerEntry
                );
            }

            String peerId = parts[0].trim();
            String peerUrl = parts[1].trim();

            if (peerId.isEmpty() || peerUrl.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid peer configuration: " + peerEntry
                );
            }

            // Não registar o próprio servidor como peer remoto.
            if (peerId.equals(identity.getPeerId())) {
                continue;
            }

            registry.registerPeer(
                    new PeerNode(peerId, URI.create(peerUrl))
            );
        }
    }
}

