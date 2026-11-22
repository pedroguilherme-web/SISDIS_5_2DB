package aisafe.airports.infrastructure.messaging;

import aisafe.airports.infrastructure.persistence.jpa.AirportReadView;
import aisafe.airports.infrastructure.persistence.jpa.AirportReadViewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AirportReadModelProjectionListenerTest {

    @Mock
    private AirportReadViewRepository repository;

    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    @Test
    void appliesAirportEventToReadModel() throws Exception {
        when(repository.findById("P2T")).thenReturn(Optional.empty());
        AirportReadModelProjectionListener listener =
                new AirportReadModelProjectionListener(objectMapper, repository);

        listener.project(payload(
                "P2T",
                "P2 Test Airport",
                "OPERATIONAL",
                "2026-11-18T10:00:00Z"
        ));

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(view ->
                view.getIataCode().equals("P2T")
                        && view.getName().equals("P2 Test Airport")
                        && view.getStatus().equals("OPERATIONAL")));
    }

    @Test
    void ignoresOutOfOrderAirportEvent() throws Exception {
        Instant latest = Instant.parse("2026-11-18T10:00:00Z");
        AirportReadView view = new AirportReadView(
                "P2T",
                "Newest",
                "Porto",
                "Portugal",
                "Europe",
                "Europe/Lisbon",
                "UNDER_MAINTENANCE",
                latest
        );
        when(repository.findById("P2T")).thenReturn(Optional.of(view));
        AirportReadModelProjectionListener listener =
                new AirportReadModelProjectionListener(objectMapper, repository);

        listener.project(payload(
                "AirportStatusUpdated",
                "P2T",
                "Old Airport",
                "OPERATIONAL",
                "2026-11-18T09:00:00Z"
        ));

        assertEquals("Newest", view.getName());
        assertEquals("UNDER_MAINTENANCE", view.getStatus());
        verify(repository).save(view);
    }

    @Test
    void retainsDeleteTombstoneAndIgnoresEarlierAirportEvents() throws Exception {
        Instant deletedAt = Instant.parse("2026-11-18T11:00:00Z");
        AirportReadView view = new AirportReadView(
                "P2T",
                "P2 Test Airport",
                "Porto",
                "Portugal",
                "Europe",
                "Europe/Lisbon",
                "OPERATIONAL",
                deletedAt,
                true
        );
        when(repository.findById("P2T")).thenReturn(Optional.of(view));
        AirportReadModelProjectionListener listener =
                new AirportReadModelProjectionListener(objectMapper, repository);

        listener.project(payload(
                "P2T",
                "Old Airport",
                "OPERATIONAL",
                "2026-11-18T10:00:00Z"
        ));

        assertTrue(view.isDeleted());
        assertEquals(deletedAt, view.getUpdatedAt());
        assertFalse(view.getName().equals("Old Airport"));
        verify(repository).save(view);
    }

    @Test
    void marksAirportDeletedWhenDeleteEventArrives() throws Exception {
        AirportReadView view = new AirportReadView(
                "P2T",
                "P2 Test Airport",
                "Porto",
                "Portugal",
                "Europe",
                "Europe/Lisbon",
                "OPERATIONAL",
                Instant.parse("2026-11-18T10:00:00Z")
        );
        when(repository.findById("P2T")).thenReturn(Optional.of(view));
        AirportReadModelProjectionListener listener =
                new AirportReadModelProjectionListener(objectMapper, repository);

        listener.project(payload(
                "AirportDeleted",
                "P2T",
                "P2 Test Airport",
                "OPERATIONAL",
                "2026-11-18T11:00:00Z"
        ));

        assertTrue(view.isDeleted());
        verify(repository).save(view);
    }

    private byte[] payload(
            String iataCode,
            String name,
            String status,
            String occurredAt) throws Exception {
        return payload("AirportStatusUpdated", iataCode, name, status, occurredAt);
    }

    private byte[] payload(
            String eventType,
            String iataCode,
            String name,
            String status,
            String occurredAt) throws Exception {
        return objectMapper.writeValueAsBytes(new AirportChangedEvent(
                "event-1",
                "correlation-1",
                eventType,
                iataCode,
                name,
                "Porto",
                "Portugal",
                "Europe",
                "Europe/Lisbon",
                status,
                Instant.parse(occurredAt)
        ));
    }
}
