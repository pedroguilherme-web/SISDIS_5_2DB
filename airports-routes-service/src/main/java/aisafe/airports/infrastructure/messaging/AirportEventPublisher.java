package aisafe.airports.infrastructure.messaging;

import aisafe.airports.application.dtos.AirportResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name = "aisafe.messaging.enabled", havingValue = "true")
public class AirportEventPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(AirportEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final String exchange;
    private final String routingKey;

    public AirportEventPublisher(
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${aisafe.messaging.airports-exchange}") String exchange,
            @Value("${aisafe.messaging.airports-routing-key}") String routingKey) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public void publish(AirportResponse airport, String eventType) {
        String correlationId = UUID.randomUUID().toString();
        AirportChangedEvent event = new AirportChangedEvent(
                UUID.randomUUID().toString(),
                correlationId,
                eventType,
                airport.iataCode(),
                airport.name(),
                airport.city(),
                airport.country(),
                airport.region(),
                airport.timezone(),
                airport.status(),
                Instant.now()
        );

        try {
            byte[] payload = objectMapper.writeValueAsBytes(event);
            rabbitTemplate.convertAndSend(exchange, routingKey, payload, message -> {
                MessageProperties properties = message.getMessageProperties();
                properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
                properties.setCorrelationId(correlationId);
                properties.setHeader("eventType", eventType);
                properties.setHeader("aggregateId", airport.iataCode());
                return message;
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize airport event", exception);
        } catch (AmqpException exception) {
            log.warn(
                    "Airport event {} could not be published; the read model may be stale",
                    event.eventId(),
                    exception
            );
        }
    }
}
