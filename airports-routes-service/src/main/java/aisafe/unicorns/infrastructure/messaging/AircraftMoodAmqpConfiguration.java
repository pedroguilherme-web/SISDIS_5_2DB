package aisafe.unicorns.infrastructure.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "aisafe.messaging.enabled", havingValue = "true")
public class AircraftMoodAmqpConfiguration {

    @Bean
    TopicExchange aircraftEventsExchange(
            @Value("${aisafe.messaging.aircraft-exchange}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    Queue aircraftMoodQueue(@Value("${aisafe.messaging.aircraft-mood-queue}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    Binding aircraftMoodBinding(
            @Qualifier("aircraftMoodQueue") Queue aircraftMoodQueue,
            @Qualifier("aircraftEventsExchange") TopicExchange aircraftEventsExchange,
            @Value("${aisafe.messaging.aircraft-mood-routing-key}") String routingKey) {
        return BindingBuilder.bind(aircraftMoodQueue).to(aircraftEventsExchange).with(routingKey);
    }
}
