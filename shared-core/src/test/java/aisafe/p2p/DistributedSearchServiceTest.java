
package aisafe.p2p;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DistributedSearchServiceTest {

    @Test
    void shouldSearchMultiplePeersAndIgnoreFailures()
            throws Exception {

        HttpServer server1 = createServer(
                "[{\"code\":\"OPO\"}]"
        );

        HttpServer server2 = createServer(
                "[{\"code\":\"LIS\"}]"
        );

        server1.start();
        server2.start();

        try {
            PeerRegistry registry = new PeerRegistry();

            registry.registerPeer(new PeerNode(
                    "airports-1",
                    URI.create("http://127.0.0.1:"
                            + server1.getAddress().getPort())
            ));

            registry.registerPeer(new PeerNode(
                    "airports-2",
                    URI.create("http://127.0.0.1:"
                            + server2.getAddress().getPort())
            ));

            // Simulates an unavailable peer.
            registry.registerPeer(new PeerNode(
                    "airports-offline",
                    URI.create("http://127.0.0.1:1")
            ));

            DistributedSearchService service =
                    new DistributedSearchService(
                            registry, new PeerClient()
                    );

            List<String> results =
                    service.searchAllPeers("/api/airports");

            assertEquals(2, results.size());
            assertTrue(results.contains(
                    "[{\"code\":\"OPO\"}]"
            ));
            assertTrue(results.contains(
                    "[{\"code\":\"LIS\"}]"
            ));

        } finally {
            server1.stop(0);
            server2.stop(0);
        }
    }

    private HttpServer createServer(String body)
            throws Exception {

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );

        server.createContext("/api/airports", exchange -> {
            byte[] response =
                    body.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json"
            );

            exchange.sendResponseHeaders(
                    200, response.length
            );

            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });

        return server;
    }
}
