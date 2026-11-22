package aisafe.routes.infrastructure.messaging;

import aisafe.routes.application.dtos.RouteResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "aisafe.messaging.enabled", havingValue = "true")
public class RouteEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RouteEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;
    private final String routingKey;

    public RouteEventPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${aisafe.messaging.routes-exchange}") String exchange,
            @Value("${aisafe.messaging.routes-routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void publish(RouteResponse route, String eventType) {
        String correlationId = UUID.randomUUID().toString();
        RouteChangedEvent event = new RouteChangedEvent(
                UUID.randomUUID().toString(),
                correlationId,
                eventType,
                route.originIataCode(),
                route.destinationIataCode(),
                route.estimatedFlightTime(),
                route.minimumRange(),
                route.minimumCapacity(),
                route.status() == null ? null : route.status().name(),
                Instant.now()
        );
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey, objectMapper.writeValueAsBytes(event), message -> {
                MessageProperties properties = message.getMessageProperties();
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setCorrelationId(correlationId);
                properties.setHeader("eventType", eventType);
                properties.setHeader("aggregateId", route.originIataCode() + "-" + route.destinationIataCode());
                return message;
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize route event", exception);
        } catch (AmqpException exception) {
            log.warn("Route event {} could not be published; the read model may be stale",
                    event.eventId(), exception);
        }
    }
}
