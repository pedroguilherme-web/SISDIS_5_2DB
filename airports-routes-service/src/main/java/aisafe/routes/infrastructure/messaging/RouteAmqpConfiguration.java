package aisafe.routes.infrastructure.messaging;

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
public class RouteAmqpConfiguration {

    @Bean
    TopicExchange routesEventsExchange(
            @Value("${aisafe.messaging.routes-exchange}") String exchange) {
        return new TopicExchange(exchange, true, false);
    }

    @Bean
    Queue routesReadQueue(@Value("${aisafe.messaging.routes-queue}") String queue) {
        return new Queue(queue, true);
    }

    @Bean
    Binding routesReadBinding(
            @Qualifier("routesReadQueue") Queue queue,
            @Qualifier("routesEventsExchange") TopicExchange exchange,
            @Value("${aisafe.messaging.routes-routing-key}") String routingKey) {
        return BindingBuilder.bind(queue).to(exchange).with(routingKey);
    }
}
