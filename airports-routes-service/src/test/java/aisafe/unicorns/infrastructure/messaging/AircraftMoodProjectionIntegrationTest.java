package aisafe.unicorns.infrastructure.messaging;

import aisafe.unicorns.infrastructure.persistence.AirplaneMoodViewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class AircraftMoodProjectionIntegrationTest {

    @Autowired
    private AirplaneMoodViewRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void persistsEventAuditFieldsAndRemainsIdempotentForDuplicateAndOlderEvents() throws Exception {
        AircraftMoodProjectionListener listener =
                new AircraftMoodProjectionListener(objectMapper, repository);

        listener.project(payload(
                "event-2",
                "correlation-2",
                "AVAILABLE",
                "2026-10-02T10:00:00Z"));
        listener.project(payload(
                "event-2",
                "correlation-2",
                "AVAILABLE",
                "2026-10-02T10:00:00Z"));
        listener.project(payload(
                "event-1",
                "correlation-1",
                "MAINTENANCE",
                "2026-10-01T10:00:00Z"));

        var view = repository.findById("DEMO-001").orElseThrow();
        assertEquals(1, repository.count());
        assertEquals("AVAILABLE", view.getMood());
        assertEquals("event-2", view.getLastEventId());
        assertEquals("correlation-2", view.getLastCorrelationId());
        assertEquals(Instant.parse("2026-10-02T10:00:00Z"), view.getUpdatedAt());
    }

    private byte[] payload(
            String eventId,
            String correlationId,
            String moodStatus,
            String timestamp) throws Exception {
        return objectMapper.writeValueAsBytes(new AircraftMoodChangedEvent(
                eventId,
                correlationId,
                "DEMO-001",
                moodStatus,
                Instant.parse(timestamp)));
    }
}
