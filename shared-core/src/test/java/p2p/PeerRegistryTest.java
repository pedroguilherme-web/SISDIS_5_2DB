
package aisafe.p2p;

import org.junit.jupiter.api.Test;
import java.net.URI;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PeerRegistryTest {

    @Test
    void shouldRegisterPeers() {
        PeerRegistry registry = new PeerRegistry();

        PeerNode peer1 = new PeerNode(
                "airports-1",
                URI.create("http://localhost:8081")
        );

        PeerNode peer2 = new PeerNode(
                "airports-2",
                URI.create("http://localhost:8082")
        );

        registry.registerPeer(peer1);
        registry.registerPeer(peer2);

        assertEquals(2, registry.getPeerCount());
        assertEquals(
                peer1,
                registry.getPeer("airports-1")
        );
    }

    @Test
    void shouldRemovePeer() {
        PeerRegistry registry = new PeerRegistry();

        registry.registerPeer(new PeerNode(
                "airports-1",
                URI.create("http://localhost:8081")
        ));

        registry.removePeer("airports-1");

        assertEquals(0, registry.getPeerCount());
    }
}
