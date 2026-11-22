package aisafe.shared.infrastructure.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ messaging configuration.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME = "aisafe.exchange";
    public static final String AIRCRAFT_QUEUE = "aisafe.aircraft.events.queue";
    public static final String MAINTENANCE_QUEUE = "aisafe.maintenance.events.queue";
    public static final String CQRS_PROJECTIONS_QUEUE = "aisafe.cqrs.projections.queue";

    public static final String ROUTING_KEY_AIRCRAFT_REGISTERED = "aircraft.registered";
    public static final String ROUTING_KEY_AIRCRAFT_STATUS_CHANGED = "aircraft.status.changed";
    public static final String ROUTING_KEY_MAINTENANCE_SCHEDULED = "maintenance.scheduled";
    public static final String ROUTING_KEY_MAINTENANCE_COMPLETED = "maintenance.completed";
    public static final String ROUTING_KEY_FLIGHT_COMPLETED = "flight.completed";

    @Bean
    public TopicExchange aisafeExchange(@Value("${aisafe.rabbitmq.exchange:" + EXCHANGE_NAME + "}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue aircraftEventsQueue() {
        return QueueBuilder.durable(AIRCRAFT_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .build();
    }

    @Bean
    public Queue maintenanceEventsQueue() {
        return QueueBuilder.durable(MAINTENANCE_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .build();
    }

    @Bean
    public Queue cqrsProjectionsQueue() {
        return QueueBuilder.durable(CQRS_PROJECTIONS_QUEUE)
                .withArgument("x-dead-letter-exchange", "")
                .build();
    }

    @Bean
    public Binding aircraftBinding(Queue aircraftEventsQueue, TopicExchange aisafeExchange) {
        return BindingBuilder.bind(aircraftEventsQueue).to(aisafeExchange).with("aircraft.#");
    }

    @Bean
    public Binding maintenanceBinding(Queue maintenanceEventsQueue, TopicExchange aisafeExchange) {
        return BindingBuilder.bind(maintenanceEventsQueue).to(aisafeExchange).with("maintenance.#");
    }

    @Bean
    public Binding cqrsProjectionsBinding(Queue cqrsProjectionsQueue, TopicExchange aisafeExchange) {
        return BindingBuilder.bind(cqrsProjectionsQueue).to(aisafeExchange).with("#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
