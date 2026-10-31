
package aisafe.p2p;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PeerClientTest {

    @Test
    void shouldSearchAnotherPeer() throws Exception {
        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );

        server.createContext("/api/airports", exchange -> {
            byte[] response = "[{\"code\":\"OPO\"}]"
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
            PeerNode peer = new PeerNode(
                    "airports-1",
                    URI.create(
                            "http://127.0.0.1:"
                                    + server.getAddress().getPort()
                    )
            );

            PeerClient client = new PeerClient();

            String result = client.searchPeer(
                    peer, "/api/airports"
            );

            assertEquals("[{\"code\":\"OPO\"}]", result);

        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldForwardSearchWithReducedTtl() throws Exception {

        AtomicReference<String> body = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();

        HttpServer server = HttpServer.create(
                new InetSocketAddress("127.0.0.1", 0), 0
        );

        server.createContext("/api/p2p/search", exchange -> {
            authorization.set(
                    exchange.getRequestHeaders()
                            .getFirst("Authorization")
            );

            body.set(new String(
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
            PeerNode peer = new PeerNode(
                    "peer-1",
                    URI.create(
                            "http://127.0.0.1:"
                                    + server.getAddress().getPort()
                    )
            );

            DistributedSearchRequest request =
                    DistributedSearchRequest.create(
                            "/api/airports", 2
                    );

            // Chave utilizada apenas neste teste
            byte[] key = new byte[32];
            Arrays.fill(key, (byte) 7);

            String testSecret = Base64.getEncoder()
                    .encodeToString(key);

            PeerTokenService tokenService =
                    new PeerTokenService(testSecret, 300000);

            PeerIdentity identity = new PeerIdentity("peer-test");

            PeerClient client = new PeerClient(
                    tokenService, identity
            );

            String result = client.forwardSearch(peer, request);

            assertEquals("[]", result);

            // Verificar que o token foi enviado
            assertNotNull(authorization.get());
            assertTrue(authorization.get().startsWith("Bearer "));

            String token = authorization.get().substring(7);

            assertTrue(tokenService.isTokenValid(token));
            assertEquals("peer-test", tokenService.extractPeerId(token));

            // Verificar que o requestId e o TTL estão corretos
            var json = new ObjectMapper().readTree(body.get());

            assertEquals(
                    request.getRequestId(),
                    json.get("requestId").asText()
            );

            assertEquals(
                    "/api/airports",
                    json.get("path").asText()
            );

            assertEquals(1, json.get("ttl").asInt());
            assertEquals(2, request.getTtl());

        } finally {
            server.stop(0);
        }
    }
}
