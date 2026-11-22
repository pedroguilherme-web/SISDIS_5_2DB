package aisafe.flights.infrastructure.messaging;

import org.apache.qpid.server.SystemLauncher;
import org.apache.qpid.server.model.SystemConfig;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public final class EmbeddedAmqpBroker implements AutoCloseable {
    private final SystemLauncher launcher = new SystemLauncher();
    private final int port;

    private EmbeddedAmqpBroker(int port) {
        this.port = port;
    }

    public static EmbeddedAmqpBroker start() {
        return start(freePort());
    }

    public static EmbeddedAmqpBroker start(int port) {
        EmbeddedAmqpBroker broker = new EmbeddedAmqpBroker(port);
        broker.startup();
        return broker;
    }

    public static void main(String[] args) throws InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 5672;
        EmbeddedAmqpBroker broker = start(port);
        Runtime.getRuntime().addShutdownHook(new Thread(broker::close));
        System.out.println("AMQP broker listening on port " + port);
        Thread.currentThread().join();
    }

    private void startup() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(SystemConfig.TYPE, "Memory");
        attributes.put(SystemConfig.INITIAL_CONFIGURATION_LOCATION,
                Objects.requireNonNull(getClass().getClassLoader().getResource("qpid-embedded-config.json")).toExternalForm());
        attributes.put(SystemConfig.STARTUP_LOGGED_TO_SYSTEM_OUT, false);
        attributes.put(SystemConfig.CONTEXT, Map.of(
                "qpid.amqp_port", String.valueOf(port),
                // Accept RabbitMQ-specific queue arguments (x-dead-letter-exchange) like RabbitMQ does.
                "queue.behaviourOnUnknownDeclareArgument", "IGNORE",
                "qpid.work_dir", System.getProperty("java.io.tmpdir") + "/qpid-flight-ops-" + port));
        try {
            launcher.startup(attributes);
        } catch (Exception e) {
            throw new IllegalStateException("Could not start the embedded AMQP broker", e);
        }
    }

    public int port() {
        return port;
    }

    @Override
    public void close() {
        launcher.shutdown();
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
