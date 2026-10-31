
package aisafe.p2p;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PeerIdentityTest {

    @Test
    void shouldCreatePeerIdentity() {
        PeerIdentity identity = new PeerIdentity("peer-1");

        assertEquals("peer-1", identity.getPeerId());
    }

    @Test
    void shouldRejectEmptyPeerId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PeerIdentity("   ")
        );
    }

    @Test
    void shouldRejectNullPeerId() {
        assertThrows(
                NullPointerException.class,
                () -> new PeerIdentity(null)
        );
    }
}
