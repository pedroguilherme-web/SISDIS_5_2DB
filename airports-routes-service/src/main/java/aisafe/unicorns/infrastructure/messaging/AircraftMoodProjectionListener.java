package aisafe.unicorns.infrastructure.messaging;

import aisafe.unicorns.infrastructure.persistence.AirplaneMoodView;
import aisafe.unicorns.infrastructure.persistence.AirplaneMoodViewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;

@Component
@ConditionalOnProperty(name = "aisafe.messaging.enabled", havingValue = "true")
public class AircraftMoodProjectionListener {

    private final ObjectMapper objectMapper;
    private final AirplaneMoodViewRepository repository;

    public AircraftMoodProjectionListener(
            ObjectMapper objectMapper,
            AirplaneMoodViewRepository repository) {
        this.objectMapper = objectMapper;
        this.repository = repository;
    }

    @RabbitListener(queues = "${aisafe.messaging.aircraft-mood-queue}")
    @Transactional
    public void project(byte[] payload) throws IOException {
        AircraftMoodChangedEvent event = objectMapper.readValue(payload, AircraftMoodChangedEvent.class);
        if (event.eventId() == null || event.eventId().isBlank()
                || event.correlationId() == null || event.correlationId().isBlank()
                || event.registration() == null || event.registration().isBlank()
                || event.timestamp() == null
                || event.moodStatus() == null || event.moodStatus().isBlank()) {
            throw new IllegalArgumentException(
                    "Aircraft mood events require eventId, correlationId, registration, moodStatus and timestamp.");
        }

        AirplaneMoodView view = repository.findById(event.registration())
                .orElseGet(() -> new AirplaneMoodView(
                        event.registration(),
                        event.moodStatus(),
                        event.timestamp(),
                        event.eventId(),
                        event.correlationId()));
        if (event.timestamp().isAfter(view.getUpdatedAt())) {
            view.applyMood(
                    event.moodStatus(),
                    event.timestamp(),
                    event.eventId(),
                    event.correlationId());
        }
        repository.save(view);
    }
}
