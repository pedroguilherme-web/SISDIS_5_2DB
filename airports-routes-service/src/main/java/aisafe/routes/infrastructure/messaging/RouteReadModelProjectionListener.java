package aisafe.routes.infrastructure.messaging;

import aisafe.routes.infrastructure.persistence.jpa.RouteReadView;
import aisafe.routes.infrastructure.persistence.jpa.RouteReadViewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "aisafe.messaging.enabled", havingValue = "true")
public class RouteReadModelProjectionListener {

    private final ObjectMapper objectMapper;
    private final RouteReadViewRepository repository;

    public RouteReadModelProjectionListener(
            ObjectMapper objectMapper,
            RouteReadViewRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @RabbitListener(queues = "${aisafe.messaging.routes-queue}")
    @Transactional
    public void project(byte[] payload) throws Exception {
        RouteChangedEvent event = objectMapper.readValue(payload, RouteChangedEvent.class);
        if (event.eventId() == null || event.eventId().isBlank()
                || event.correlationId() == null || event.correlationId().isBlank()
                || event.originIataCode() == null || event.originIataCode().isBlank()
                || event.destinationIataCode() == null || event.destinationIataCode().isBlank()
                || event.eventType() == null || event.eventType().isBlank()
                || event.occurredAt() == null) {
            throw new IllegalArgumentException(
                    "Route events require eventId, correlationId, eventType, origin, destination and occurredAt");
        }

        String routeKey = event.originIataCode() + "-" + event.destinationIataCode();
        RouteReadView view = repository.findById(routeKey)
                .orElseGet(() -> new RouteReadView(routeKey));
        view.apply(event);
        repository.save(view);
    }
}
