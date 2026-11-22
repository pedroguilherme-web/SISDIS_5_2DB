package aisafe.shared.infrastructure.messaging;

import aisafe.shared.domain.events.DomainEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Resilient Domain Event Publisher.
 * Publishes events over AMQP to RabbitMQ and also broadcasts locally via Spring ApplicationEventPublisher.
 * Ensures graceful fallback if RabbitMQ broker is offline during tests or standalone dev.
 */
@Component
public class DomainEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(DomainEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${aisafe.rabbitmq.exchange:" + RabbitMQConfig.EXCHANGE_NAME + "}")
    private String exchangeName;

    @Autowired
    public DomainEventPublisher(RabbitTemplate rabbitTemplate, ApplicationEventPublisher eventPublisher) {
        this.rabbitTemplate = rabbitTemplate;
        this.eventPublisher = eventPublisher;
    }

    public void publish(String routingKey, DomainEvent event) {
        log.info("Publishing DomainEvent [type={}, id={}] with routing key '{}'",
                event.getEventType(), event.getEventId(), routingKey);

        // 1. Publish locally inside Spring context (immediate in-process projection update)
        try {
            if (eventPublisher != null) {
                eventPublisher.publishEvent(event);
            }
        } catch (Exception e) {
            log.warn("Local event publication failed: {}", e.getMessage());
        }

        // 2. Publish over AMQP to RabbitMQ broker
        try {
            if (rabbitTemplate != null) {
                rabbitTemplate.convertAndSend(exchangeName, routingKey, event);
                log.debug("AMQP message published successfully to exchange '{}'", exchangeName);
            }
        } catch (Exception e) {
            log.warn("Could not publish event to RabbitMQ (Broker might be offline): {}", e.getMessage());
        }
    }
}
