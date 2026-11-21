package aisafe.airports.infrastructure.messaging;

import aisafe.airports.infrastructure.persistence.jpa.AirportReadView;
import aisafe.airports.infrastructure.persistence.jpa.AirportReadViewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "aisafe.messaging.enabled", havingValue = "true")
public class AirportReadModelProjectionListener {

    private final ObjectMapper objectMapper;
    private final AirportReadViewRepository repository;

    public AirportReadModelProjectionListener(
            ObjectMapper objectMapper,
            AirportReadViewRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(
                    name = "${aisafe.messaging.airports-queue}",
                    durable = "true"),
            exchange = @Exchange(
                    name = "${aisafe.messaging.airports-exchange}",
                    type = "topic",
                    durable = "true"),
            key = "${aisafe.messaging.airports-routing-key}"
    ))
    @Transactional
    public void project(byte[] payload) throws Exception {
        AirportChangedEvent event =
                objectMapper.readValue(payload, AirportChangedEvent.class);

        if (event.eventId() == null || event.eventId().isBlank()
                || event.correlationId() == null
                || event.correlationId().isBlank()
                || event.iataCode() == null
                || event.iataCode().isBlank()
                || event.occurredAt() == null) {
            throw new IllegalArgumentException(
                    "Airport events require eventId, correlationId, iataCode and occurredAt");
        }

        AirportReadView view = repository.findById(event.iataCode())
                .orElseGet(() -> new AirportReadView(
                        event.iataCode(),
                        event.name(),
                        event.city(),
                        event.country(),
                        event.region(),
                        event.timezone(),
                        event.status(),
                        event.occurredAt(),
                        "AirportDeleted".equals(event.eventType())));

        view.apply(
                event.name(),
                event.city(),
                event.country(),
                event.region(),
                event.timezone(),
                event.status(),
                event.occurredAt(),
                "AirportDeleted".equals(event.eventType())
        );
        repository.save(view);
    }
}
