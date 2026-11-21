
package aisafe;

import aisafe.p2p.DistributedSearchRequest;
import aisafe.p2p.PeerClient;
import aisafe.p2p.PeerIdentity;
import aisafe.p2p.PeerNode;
import aisafe.p2p.PeerTokenService;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class P2PRealCommunicationTest {

    @Test
    void shouldCommunicateWithRealPeer() {

        String secret = System.getenv("P2P_JWT_SECRET");

        assertNotNull(secret,
                "A variavel P2P_JWT_SECRET deve estar definida");

        PeerTokenService tokenService =
                new PeerTokenService(secret, 300000);

        PeerClient client = new PeerClient(
                tokenService,
                new PeerIdentity("peer-1")
        );

        PeerNode peer2 = new PeerNode(
                "peer-2",
                URI.create("http://localhost:8093")
        );

        DistributedSearchRequest request =
                new DistributedSearchRequest(
                        UUID.randomUUID().toString(),
                        "/api/airports/search",
                        1
                );

        String response = client.forwardSearch(peer2, request);

        assertNotNull(response);
        assertTrue(response.trim().startsWith("["));

        System.out.println(
                "Comunicacao P2P real efetuada com sucesso!"
        );
        System.out.println("Resposta do Peer 2: " + response);
    }
}
