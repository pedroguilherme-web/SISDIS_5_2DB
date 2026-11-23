package aisafe.flights.infrastructure.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EmbeddedAmqpBrokerSmokeTest {
    @Test
    void shouldRouteAMessageThroughATopicExchange() {
        try (EmbeddedAmqpBroker broker = EmbeddedAmqpBroker.start()) {
            CachingConnectionFactory connectionFactory = new CachingConnectionFactory("localhost", broker.port());
            connectionFactory.setUsername("guest");
            connectionFactory.setPassword("guest");
            try {
                RabbitAdmin admin = new RabbitAdmin(connectionFactory);
                TopicExchange exchange = new TopicExchange("smoke.exchange", true, false);
                Queue queue = new Queue("smoke.queue", true);
                admin.declareExchange(exchange);
                admin.declareQueue(queue);
                admin.declareBinding(BindingBuilder.bind(queue).to(exchange).with("flight.#"));

                RabbitTemplate template = new RabbitTemplate(connectionFactory);
                template.convertAndSend("smoke.exchange", "flight.completed", "hello".getBytes(StandardCharsets.UTF_8));

                byte[] received = (byte[]) template.receiveAndConvert("smoke.queue", 5000);
                assertEquals("hello", new String(received, StandardCharsets.UTF_8));
            } finally {
                connectionFactory.destroy();
            }
        }
    }
}
