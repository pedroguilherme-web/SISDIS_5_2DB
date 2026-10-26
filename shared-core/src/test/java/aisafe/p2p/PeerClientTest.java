
package aisafe.p2p;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;

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
                    URI.create("http://127.0.0.1:"
                            + server.getAddress().getPort())
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
}
