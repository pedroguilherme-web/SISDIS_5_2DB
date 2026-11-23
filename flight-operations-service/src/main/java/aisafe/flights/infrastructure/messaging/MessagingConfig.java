package aisafe.flights.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import static aisafe.flights.infrastructure.messaging.MessagingTopology.*;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "aisafe.messaging.enabled", havingValue = "true")
public class MessagingConfig {
    @Bean
    public Declarables flightOperationsTopology(
            @Value("${aisafe.messaging.flight-queue}") String flightQueueName,
            @Value("${aisafe.messaging.aircraft-queue}") String aircraftQueueName,
            @Value("${aisafe.messaging.route-queue}") String routeQueueName) {
        TopicExchange bus = new TopicExchange(BUS_EXCHANGE, true, false);
        TopicExchange routes = new TopicExchange(ROUTES_EXCHANGE, true, false);

        // One durable queue per instance: every replica gets every event (publish/subscribe), even after being down.
        Queue flightQueue = QueueBuilder.durable(flightQueueName).build();
        Queue aircraftQueue = QueueBuilder.durable(aircraftQueueName).build();
        Queue routeQueue = QueueBuilder.durable(routeQueueName).build();

        Binding flightScheduled = BindingBuilder.bind(flightQueue).to(bus).with(FLIGHT_SCHEDULED);
        Binding flightStatusChanged = BindingBuilder.bind(flightQueue).to(bus).with(FLIGHT_STATUS_CHANGED);
        Binding aircraftRegistered = BindingBuilder.bind(aircraftQueue).to(bus).with(AIRCRAFT_REGISTERED);
        Binding aircraftStatusChanged = BindingBuilder.bind(aircraftQueue).to(bus).with(AIRCRAFT_STATUS_CHANGED);
        Binding routeChanged = BindingBuilder.bind(routeQueue).to(routes).with(ROUTE_CHANGED_PATTERN);

        return new Declarables(bus, routes, flightQueue, aircraftQueue, routeQueue,
                flightScheduled, flightStatusChanged, aircraftRegistered, aircraftStatusChanged, routeChanged);
    }
}
