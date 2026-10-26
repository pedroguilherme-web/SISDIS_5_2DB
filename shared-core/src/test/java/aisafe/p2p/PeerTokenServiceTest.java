
package aisafe.p2p;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class PeerTokenServiceTest {

    private PeerTokenService createService() {
        byte[] key = new byte[32];
        java.util.Arrays.fill(key, (byte) 7);

        String secret = Base64.getEncoder().encodeToString(key);

        return new PeerTokenService(secret, 300000);
    }

    @Test
    void shouldGenerateValidToken() {
        PeerTokenService service = createService();

        String token = service.generateToken(
                new PeerIdentity("peer-1")
        );

        assertTrue(service.isTokenValid(token));
    }

    @Test
    void shouldRejectInvalidToken() {
        PeerTokenService service = createService();

        assertFalse(service.isTokenValid("invalid-token"));
    }

    @Test
    void shouldRejectTokenSignedWithDifferentKey() {
        PeerTokenService serviceA = createService();

        byte[] otherKey = new byte[32];
        java.util.Arrays.fill(otherKey, (byte) 9);

        PeerTokenService serviceB = new PeerTokenService(
                Base64.getEncoder().encodeToString(otherKey),
                300000
        );

        String token = serviceA.generateToken(
                new PeerIdentity("peer-1")
        );

        assertFalse(serviceB.isTokenValid(token));
    }
}
