
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

        java.util.concurrent.atomic.AtomicInteger requestCount =
                new java.util.concurrent.atomic.AtomicInteger();

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );

        server.createContext("/api/p2p/search", exchange -> {
            requestCount.incrementAndGet();

            // Consumir o corpo do pedido
            exchange.getRequestBody().readAllBytes();

            byte[] response =
                    "[{\"code\":\"OPO\"}]"
                            .getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json"
            );

            exchange.sendResponseHeaders(200, response.length);

            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });

        server.start();

        try {
            PeerRegistry registry = new PeerRegistry();

            registry.registerPeer(new PeerNode(
                    "airports-1",
                    URI.create(
                            "http://127.0.0.1:"
                                    + server.getAddress().getPort()
                    )
            ));

            // Criar um cliente P2P autenticado para o teste
            byte[] key = new byte[32];
            java.util.Arrays.fill(key, (byte) 7);

            String testSecret = java.util.Base64.getEncoder()
                    .encodeToString(key);

            PeerTokenService tokenService =
                    new PeerTokenService(testSecret, 300000);

            PeerIdentity identity = new PeerIdentity("peer-test");

            PeerClient client = new PeerClient(
                    tokenService, identity
            );

            DistributedSearchService service =
                    new DistributedSearchService(registry, client);

            DistributedSearchRequest request =
                    DistributedSearchRequest.create(
                            "/api/airports", 2
                    );

            String first = service.processSearch(request);
            String second = service.processSearch(request);

            // O primeiro pedido recebe a resposta do peer
            assertTrue(first.contains("OPO"));

            // O pedido repetido não é processado novamente
            assertEquals("[]", second);

            // Apenas um POST foi enviado ao peer
            assertEquals(1, requestCount.get());

        } finally {
            server.stop(0);
        }
    }



    @Test
    void shouldForwardSearchWithReducedTtlAndAvoidDuplicates()
            throws Exception {

        java.util.concurrent.atomic.AtomicInteger forwardCount =
                new java.util.concurrent.atomic.AtomicInteger();

        java.util.concurrent.atomic.AtomicReference<String> forwardedBody =
                new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.atomic.AtomicReference<String> authorization =
                new java.util.concurrent.atomic.AtomicReference<>();

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );

        server.createContext("/api/airports", exchange -> {
            byte[] response = "[]".getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json"
            );
            exchange.sendResponseHeaders(200, response.length);

            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });

        server.createContext("/api/p2p/search", exchange -> {
            forwardCount.incrementAndGet();

            authorization.set(
                    exchange.getRequestHeaders()
                            .getFirst("Authorization")
            );

            forwardedBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8
            ));

            byte[] response = "[]".getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders().set(
                    "Content-Type", "application/json"
            );
            exchange.sendResponseHeaders(200, response.length);

            try (var output = exchange.getResponseBody()) {
                output.write(response);
            }
        });

        server.start();

        try {
            PeerRegistry registry = new PeerRegistry();

            registry.registerPeer(new PeerNode(
                    "peer-2",
                    URI.create(
                            "http://127.0.0.1:"
                                    + server.getAddress().getPort()
                    )
            ));

            byte[] key = new byte[32];
            java.util.Arrays.fill(key, (byte) 7);

            String testSecret = java.util.Base64.getEncoder()
                    .encodeToString(key);

            PeerTokenService tokenService =
                    new PeerTokenService(testSecret, 300000);

            PeerIdentity identity = new PeerIdentity("peer-1");

            PeerClient client = new PeerClient(
                    tokenService, identity
            );

            DistributedSearchService service =
                    new DistributedSearchService(registry, client);

            DistributedSearchRequest request =
                    DistributedSearchRequest.create(
                            "/api/airports", 2
                    );

            service.processSearch(request);

            // O pedido foi encaminhado uma vez
            assertEquals(1, forwardCount.get());

            // O JWT foi enviado e é válido
            assertNotNull(authorization.get());
            assertTrue(authorization.get().startsWith("Bearer "));

            String token = authorization.get().substring(7);

            assertTrue(tokenService.isTokenValid(token));
            assertEquals(
                    "peer-1",
                    tokenService.extractPeerId(token)
            );

            // O requestId mantém-se e o TTL diminui
            var json = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(forwardedBody.get());

            assertEquals(
                    request.getRequestId(),
                    json.get("requestId").asText()
            );
            assertEquals(1, json.get("ttl").asInt());

            // Repetir o mesmo pedido não volta a encaminhá-lo
            assertEquals("[]", service.processSearch(request));
            assertEquals(1, forwardCount.get());

            // Com TTL zero, não deve haver encaminhamento
            DistributedSearchRequest zeroTtlRequest =
                    DistributedSearchRequest.create(
                            "/api/airports", 0
                    );

            service.processSearch(zeroTtlRequest);

            assertEquals(1, forwardCount.get());

        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldIncludeLocalResultsWhenTtlIsZero() {

        PeerRegistry registry = new PeerRegistry();
        PeerClient client = new PeerClient();

        LocalSearchProvider localSearchProvider =
                path -> "[{\"code\":\"OPO\"}]";

        DistributedSearchService service =
                new DistributedSearchService(
                        registry,
                        client,
                        localSearchProvider
                );

        DistributedSearchRequest request =
                DistributedSearchRequest.create(
                        "/api/airports/search",
                        0
                );

        String result = service.processSearch(request);

        assertEquals("[{\"code\":\"OPO\"}]", result);
    }




}
