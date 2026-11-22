package aisafe.flights.infrastructure.messaging;

import aisafe.flights.application.ChangeFlightStatusUseCase;
import aisafe.flights.application.QueryScope;
import aisafe.flights.application.events.FlightEvent;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.flights.infrastructure.persistence.jpa.catalog.AircraftCatalogRepository;
import aisafe.flights.infrastructure.persistence.jpa.catalog.RouteCatalogRepository;
import aisafe.flights.infrastructure.readmodel.FlightQueryService;
import aisafe.shared.domain.ResourceNotFoundException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("jpa")
class AmqpMessagingIntegrationTest {
    private static final String RABBITMQ_PORT = System.getenv("RABBITMQ_TEST_PORT");
    private static final EmbeddedAmqpBroker BROKER = RABBITMQ_PORT == null ? EmbeddedAmqpBroker.start() : null;
    private static final OffsetDateTime T0 = OffsetDateTime.of(2027, 3, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    @DynamicPropertySource
    static void brokerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", () -> "localhost");
        registry.add("spring.rabbitmq.port", () -> BROKER != null ? BROKER.port() : Integer.parseInt(RABBITMQ_PORT));
        registry.add("aisafe.messaging.enabled", () -> "true");
        registry.add("p2p.peer-id", () -> "it-replica-1");
        registry.add("aisafe.read-model.rebuild-on-startup", () -> "false");
    }

    @AfterAll
    static void stopBroker() {
        if (BROKER != null) {
            BROKER.close();
        }
    }

    @Autowired private RabbitTemplate rabbitTemplate;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private FlightQueryService queries;
    @Autowired private AircraftCatalogRepository aircraftCatalog;
    @Autowired private RouteCatalogRepository routeCatalog;
    @Autowired private ScheduledFlightRepository flights;
    @Autowired private ChangeFlightStatusUseCase changeStatus;

    private static Message json(String body, String sourceInstance) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        if (sourceInstance != null) {
            properties.setHeader(MessagingTopology.HEADER_SOURCE_INSTANCE, sourceInstance);
        }
        return new Message(body.getBytes(StandardCharsets.UTF_8), properties);
    }

    private static void await(BooleanSupplier condition, String description) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for: " + description);
            }
        }
        fail("Timed out waiting for: " + description);
    }

    private boolean projected(String flightId) {
        try {
            queries.flight(flightId);
            return true;
        } catch (ResourceNotFoundException e) {
            return false;
        }
    }

    @Test
    void flightScheduledOnAnotherReplicaReachesThisReadModel() {
        ScheduledFlight onOtherReplica = new ScheduledFlight(T0, T0.plusHours(2), FlightStatus.SCHEDULED,
                "LIS", "OPO", "CS-RPL", "A321");
        FlightEvent event = FlightEvent.scheduled(onOtherReplica);

        rabbitTemplate.send(MessagingTopology.BUS_EXCHANGE, MessagingTopology.FLIGHT_SCHEDULED,
                json(new String(EventJson.write(event), StandardCharsets.UTF_8), "it-replica-2"));

        await(() -> projected(onOtherReplica.getId()), "flight projected from the broker");
        assertEquals("A321", queries.flight(onOtherReplica.getId()).aircraftModel());
        assertTrue(flights.findById(onOtherReplica.getId()).isEmpty(), "the write model of this replica is untouched");
    }

    @Test
    void aircraftStatusChangeFromTheAircraftServiceUpdatesCatalogAndMood() {
        String body = """
                {"eventId":"e-1","timestamp":1795384809.123,"eventType":"AIRCRAFT_STATUS_CHANGED",
                 "registrationNumber":"CS-MNT","previousStatus":"AVAILABLE","newStatus":"UNDER_MAINTENANCE",
                 "reason":"Scheduled check"}
                """;

        rabbitTemplate.send(MessagingTopology.BUS_EXCHANGE, MessagingTopology.AIRCRAFT_STATUS_CHANGED, json(body, null));

        await(() -> aircraftCatalog.findById("CS-MNT").isPresent(), "aircraft catalog updated");
        assertEquals("UNDER_MAINTENANCE", aircraftCatalog.findById("CS-MNT").orElseThrow().getStatus());
        assertEquals("UNDER_CARE", queries.mood("CS-MNT").mood());
    }

    @Test
    void routeDeletedInTheRoutesServiceIsMarkedInTheRouteCatalog() {
        String body = """
                {"eventId":"r-1","correlationId":"c-1","eventType":"RouteDeleted","originIataCode":"FAO",
                 "destinationIataCode":"LIS","estimatedFlightTime":45,"minimumRange":300.0,"minimumCapacity":80,
                 "status":"ACTIVE","occurredAt":"2026-11-22T10:00:00Z"}
                """;

        rabbitTemplate.send(MessagingTopology.ROUTES_EXCHANGE, "routes.route.changed", json(body, null));

        await(() -> routeCatalog.findById("FAO-LIS").isPresent(), "route catalog updated");
        assertTrue(routeCatalog.findById("FAO-LIS").orElseThrow().isDeleted());
    }

    @Test
    void completingAFlightNotifiesTheAircraftServiceWithItsContract() {
        Queue aircraftServiceQueue = new Queue("it.aircraft-service.flight-completed", false, false, true);
        amqpAdmin.declareQueue(aircraftServiceQueue);
        amqpAdmin.declareBinding(BindingBuilder.bind(aircraftServiceQueue)
                .to(new TopicExchange(MessagingTopology.BUS_EXCHANGE, true, false))
                .with(MessagingTopology.FLIGHT_COMPLETED));

        ScheduledFlight flight = flights.save(new ScheduledFlight(T0, T0.plusMinutes(90), FlightStatus.IN_FLIGHT,
                "OPO", "MAD", "CS-CMP", "E190"));

        changeStatus.execute(flight.getId(), FlightStatus.COMPLETED, QueryScope.CLUSTER);

        Message received = rabbitTemplate.receive(aircraftServiceQueue.getName(), 10_000);
        assertNotNull(received, "flight.completed published");
        assertEquals("{\"flightId\":\"" + flight.getId() + "\",\"aircraftRegistration\":\"CS-CMP\",\"flightDurationHours\":1.5}",
                new String(received.getBody(), StandardCharsets.UTF_8));
        assertEquals(FlightStatus.COMPLETED, queries.flight(flight.getId()).status(), "own read model updated in-process");
    }

    @Test
    void aMalformedMessageIsDiscardedAndTheConsumerKeepsWorking() {
        rabbitTemplate.send(MessagingTopology.BUS_EXCHANGE, MessagingTopology.FLIGHT_SCHEDULED, json("{not json", "it-replica-2"));

        ScheduledFlight next = new ScheduledFlight(T0.plusDays(1), T0.plusDays(1).plusHours(1), FlightStatus.SCHEDULED,
                "LIS", "FAO", "CS-NXT", "A320");
        rabbitTemplate.send(MessagingTopology.BUS_EXCHANGE, MessagingTopology.FLIGHT_SCHEDULED,
                json(new String(EventJson.write(FlightEvent.scheduled(next)), StandardCharsets.UTF_8), "it-replica-2"));

        await(() -> projected(next.getId()), "next valid event still processed");
    }
}
