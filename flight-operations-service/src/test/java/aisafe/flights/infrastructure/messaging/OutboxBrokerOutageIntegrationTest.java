package aisafe.flights.infrastructure.messaging;

import aisafe.flights.application.ChangeFlightStatusUseCase;
import aisafe.flights.application.QueryScope;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.flights.infrastructure.persistence.jpa.outbox.OutboxMessageRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("jpa")
class OutboxBrokerOutageIntegrationTest {
    private static final int PORT = freePort();
    private static EmbeddedAmqpBroker broker = EmbeddedAmqpBroker.start(PORT);
    private static final OffsetDateTime T0 = OffsetDateTime.of(2027, 5, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    @DynamicPropertySource
    static void brokerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", () -> "localhost");
        registry.add("spring.rabbitmq.port", () -> PORT);
        registry.add("aisafe.messaging.enabled", () -> "true");
        registry.add("p2p.peer-id", () -> "outage-replica-1");
        registry.add("aisafe.read-model.rebuild-on-startup", () -> "false");
        registry.add("aisafe.outbox.relay-interval", () -> "PT3S");
    }

    @AfterAll
    static void stopBroker() {
        broker.close();
    }

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private ScheduledFlightRepository flights;
    @Autowired private ChangeFlightStatusUseCase changeStatus;
    @Autowired private OutboxMessageRepository outbox;

    @Test
    void anEventProducedWhileTheBrokerIsDownIsDeliveredWhenItComesBack() throws Exception {
        ScheduledFlight flight = flights.save(new ScheduledFlight(T0, T0.plusHours(2), FlightStatus.IN_FLIGHT,
                "LIS", "MAD", "CS-OUT", "A320"));

        broker.close();
        assertDoesNotThrow(() -> changeStatus.execute(flight.getId(), FlightStatus.COMPLETED, QueryScope.CLUSTER),
                "the command succeeds although the broker is down");
        assertEquals(2, outbox.countByPublishedAtIsNull(), "status change + flight.completed kept in the outbox");

        broker = EmbeddedAmqpBroker.start(PORT);
        Queue aircraftService = new Queue("outage.aircraft-service", false, false, true);
        amqpAdmin.declareQueue(aircraftService);
        amqpAdmin.declareBinding(BindingBuilder.bind(aircraftService)
                .to(new TopicExchange(MessagingTopology.BUS_EXCHANGE, true, false))
                .with(MessagingTopology.FLIGHT_COMPLETED));

        Message received = rabbitTemplate.receive(aircraftService.getName(), 20_000);
        assertNotNull(received, "flight.completed delivered after the broker came back");
        assertTrue(new String(received.getBody(), StandardCharsets.UTF_8).contains(flight.getId()));
        long deadline = System.currentTimeMillis() + 10_000;
        while (outbox.countByPublishedAtIsNull() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(200);
        }
        assertEquals(0, outbox.countByPublishedAtIsNull(), "nothing left pending");
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
