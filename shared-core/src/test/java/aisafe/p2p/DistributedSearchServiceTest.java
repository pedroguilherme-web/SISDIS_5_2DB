
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

    @Test
    void shouldSearchAndAggregatePeerResponses()
            throws Exception {

        HttpServer server1 = createServer(
                "[{\"code\":\"OPO\"},{\"code\":\"LIS\"}]"
        );

        HttpServer server2 = createServer(
                "[{\"code\":\"LIS\"},{\"code\":\"FAO\"}]"
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

            DistributedSearchService service =
                    new DistributedSearchService(
                            registry, new PeerClient()
                    );

            String result =
                    service.searchAndAggregate("/api/airports");

            var json = new com.fasterxml.jackson.databind
                    .ObjectMapper().readTree(result);

            assertEquals(3, json.size());

            java.util.Set<String> codes =
                    new java.util.HashSet<>();

            for (var airport : json) {
                codes.add(airport.get("code").asText());
            }

            assertEquals(
                    java.util.Set.of("OPO", "LIS", "FAO"),
                    codes
            );

        } finally {
            server1.stop(0);
            server2.stop(0);
        }
    }

    @Test
    void shouldNotProcessDuplicateSearchRequest() {

        PeerRegistry registry = new PeerRegistry();
        PeerClient client = new PeerClient();

        DistributedSearchService service =
                new DistributedSearchService(registry, client);

        DistributedSearchRequest request =
                DistributedSearchRequest.create(
                        "/api/airports", 2
                );

        String firstResult = service.processSearch(request);
        String secondResult = service.processSearch(request);

        assertEquals("[]", firstResult);
        assertEquals("[]", secondResult);
    }

    @Test
    void shouldReturnEmptyForRepeatedRequestWithPeers()
            throws Exception {

        HttpServer server = createServer(
                "[{\"code\":\"OPO\"}]"
        );

        server.start();

        try {
            PeerRegistry registry = new PeerRegistry();

            registry.registerPeer(new PeerNode(
                    "airports-1",
                    URI.create("http://127.0.0.1:"
                            + server.getAddress().getPort())
            ));

            DistributedSearchService service =
                    new DistributedSearchService(
                            registry, new PeerClient()
                    );

            DistributedSearchRequest request =
                    DistributedSearchRequest.create(
                            "/api/airports", 2
                    );

            String first = service.processSearch(request);
            String second = service.processSearch(request);

            assertTrue(first.contains("OPO"));
            assertEquals("[]", second);

        } finally {
            server.stop(0);
        }
    }



}
