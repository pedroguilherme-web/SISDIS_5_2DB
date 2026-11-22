package aisafe.unicorns.infrastructure.messaging;

import aisafe.unicorns.infrastructure.persistence.AirplaneMoodView;
import aisafe.unicorns.infrastructure.persistence.AirplaneMoodViewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AircraftMoodProjectionListenerTest {

    @Mock
    private AirplaneMoodViewRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void appliesNewerMoodEventToReadModel() throws Exception {
        Instant previousUpdate = Instant.parse("2026-10-01T10:00:00Z");
        AirplaneMoodView view = new AirplaneMoodView("DEMO-001", "calm", previousUpdate);
        when(repository.findById("DEMO-001")).thenReturn(Optional.of(view));
        AircraftMoodProjectionListener listener = new AircraftMoodProjectionListener(objectMapper, repository);

        listener.project(payload("event-2", "DEMO-001", "happy", "2026-10-02T10:00:00Z"));

        assertEquals("happy", view.getMood());
        assertEquals(Instant.parse("2026-10-02T10:00:00Z"), view.getUpdatedAt());
        assertEquals("event-2", view.getLastEventId());
        assertEquals("corr-event-2", view.getLastCorrelationId());
        verify(repository).save(view);
    }

    @Test
    void ignoresOutOfOrderMoodEvents() throws Exception {
        Instant latestUpdate = Instant.parse("2026-10-02T10:00:00Z");
        AirplaneMoodView view = new AirplaneMoodView("DEMO-001", "happy", latestUpdate);
        when(repository.findById("DEMO-001")).thenReturn(Optional.of(view));
        AircraftMoodProjectionListener listener = new AircraftMoodProjectionListener(objectMapper, repository);

        listener.project(payload("event-1", "DEMO-001", "calm", "2026-10-01T10:00:00Z"));

        assertEquals("happy", view.getMood());
        assertEquals(latestUpdate, view.getUpdatedAt());
        assertNull(view.getLastEventId());
        assertNull(view.getLastCorrelationId());
        verify(repository).save(view);
    }

    @Test
    void rejectsMalformedEventContracts() throws Exception {
        AircraftMoodProjectionListener listener = new AircraftMoodProjectionListener(objectMapper, repository);
        byte[] payload = objectMapper.writeValueAsBytes(new AircraftMoodChangedEvent(
                null, null, "DEMO-001", "happy", Instant.now()));

        assertThrows(IllegalArgumentException.class, () -> listener.project(payload));
    }

    private byte[] payload(String eventId, String registration, String mood, String occurredAt) throws Exception {
        return objectMapper.writeValueAsBytes(new AircraftMoodChangedEvent(
                eventId, "corr-" + eventId, registration, mood, Instant.parse(occurredAt)));
    }
}
