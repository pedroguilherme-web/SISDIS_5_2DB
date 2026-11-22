package aisafe.routes.infrastructure.messaging;

import aisafe.routes.infrastructure.persistence.jpa.RouteReadView;
import aisafe.routes.infrastructure.persistence.jpa.RouteReadViewRepository;
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
class RouteReadModelProjectionListenerTest {

    @Mock
    private RouteReadViewRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void projectsRouteEventAndKeepsDeletedRoutesAsTombstones() throws Exception {
        when(repository.findById("OPO-LIS")).thenReturn(Optional.empty());
        RouteReadModelProjectionListener listener =
                new RouteReadModelProjectionListener(objectMapper, repository);

        listener.project(payload("RouteCreated", "2026-11-18T10:00:00Z"));
        listener.project(payload("RouteDeleted", "2026-11-18T11:00:00Z"));

        org.mockito.ArgumentCaptor<RouteReadView> captor =
                org.mockito.ArgumentCaptor.forClass(RouteReadView.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        RouteReadView latest = captor.getAllValues().getLast();
        assertEquals("OPO-LIS", latest.getRouteKey());
        assertEquals("OPO", latest.getOriginCode());
        assertEquals("LIS", latest.getDestinationCode());
        assertTrue(latest.isDeleted());
        assertEquals(Instant.parse("2026-11-18T11:00:00Z"), latest.getUpdatedAt());
    }

    @Test
    void ignoresOutOfOrderRouteEvents() throws Exception {
        RouteReadView view = new RouteReadView("OPO-LIS");
        view.apply(event("RouteCreated", "2026-11-18T10:00:00Z"));
        when(repository.findById("OPO-LIS")).thenReturn(Optional.of(view));
        RouteReadModelProjectionListener listener =
                new RouteReadModelProjectionListener(objectMapper, repository);

        listener.project(payload("RouteDeleted", "2026-11-18T09:00:00Z"));

        assertFalse(view.isDeleted());
        assertEquals(Instant.parse("2026-11-18T10:00:00Z"), view.getUpdatedAt());
        verify(repository).save(view);
    }

    @Test
    void acceptsNewerDeleteEventAfterActiveRouteEvent() throws Exception {
        RouteReadView view = new RouteReadView("OPO-LIS");
        view.apply(event("RouteCreated", "2026-11-18T10:00:00Z"));
        when(repository.findById("OPO-LIS")).thenReturn(Optional.of(view));
        RouteReadModelProjectionListener listener =
                new RouteReadModelProjectionListener(objectMapper, repository);

        listener.project(payload("RouteDeleted", "2026-11-18T11:00:00Z"));

        assertTrue(view.isDeleted());
        verify(repository).save(view);
    }

    private byte[] payload(String eventType, String occurredAt) throws Exception {
        return objectMapper.writeValueAsBytes(event(eventType, occurredAt));
    }

    private RouteChangedEvent event(String eventType, String occurredAt) {
        return new RouteChangedEvent(
                "event-1",
                "correlation-1",
                eventType,
                "OPO",
                "LIS",
                45,
                300.0,
                150,
                "ACTIVE",
                Instant.parse(occurredAt));
    }
}
