package aisafe.aircrafts.cqrs;

import aisafe.aircrafts.cqrs.events.AircraftRegisteredEvent;
import aisafe.aircrafts.cqrs.events.AircraftStatusChangedEvent;
import aisafe.aircrafts.cqrs.projections.AircraftProjectionListener;
import aisafe.aircrafts.cqrs.projections.AircraftReadModel;
import aisafe.aircrafts.cqrs.projections.AircraftReadModelRepository;
import aisafe.maintenance.cqrs.events.MaintenanceCompletedEvent;
import aisafe.maintenance.cqrs.events.MaintenanceScheduledEvent;
import aisafe.shared.domain.events.FlightCompletedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AircraftProjectionListener CQRS Projection Tests")
class AircraftProjectionListenerTest {

    private Map<String, AircraftReadModel> storage;
    private AircraftReadModelRepository repository;
    private AircraftProjectionListener listener;

    @BeforeEach
    void setUp() {
        storage = new HashMap<>();
        repository = (AircraftReadModelRepository) Proxy.newProxyInstance(
                AircraftReadModelRepository.class.getClassLoader(),
                new Class<?>[]{AircraftReadModelRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findByRegistrationNumber".equals(name) || "findById".equals(name)) {
                        return Optional.ofNullable(storage.get(args[0]));
                    } else if ("save".equals(name)) {
                        AircraftReadModel rm = (AircraftReadModel) args[0];
                        storage.put(rm.getRegistrationNumber(), rm);
                        return rm;
                    } else if ("findAll".equals(name)) {
                        return new ArrayList<>(storage.values());
                    } else if ("count".equals(name)) {
                        return (long) storage.size();
                    } else if ("toString".equals(name)) {
                        return "FakeAircraftReadModelRepository";
                    }
                    return null;
                }
        );
        listener = new AircraftProjectionListener(repository);
    }

    @Test
    @DisplayName("Should project new read model on AircraftRegisteredEvent")
    void shouldProjectNewReadModelOnAircraftRegisteredEvent() {
        AircraftRegisteredEvent event = new AircraftRegisteredEvent(
                "CS-NEW", "Airbus A330", "AVAILABLE", 250, 6000.0, List.of("WIFI", "USB")
        );

        listener.onAircraftRegistered(event);

        AircraftReadModel saved = storage.get("CS-NEW");
        assertNotNull(saved);
        assertEquals("CS-NEW", saved.getRegistrationNumber());
        assertEquals("Airbus A330", saved.getModelName());
        assertEquals("READY_FOR_FLIGHT", saved.getReadinessStatus());
    }

    @Test
    @DisplayName("Should update status and recalculate readiness on AircraftStatusChangedEvent")
    void shouldUpdateStatusOnAircraftStatusChangedEvent() {
        AircraftReadModel model = new AircraftReadModel("CS-MOD", "Boeing 777", "AVAILABLE", 50.0, 0);
        storage.put("CS-MOD", model);

        AircraftStatusChangedEvent event = new AircraftStatusChangedEvent(
                "CS-MOD", "AVAILABLE", "UNDER_MAINTENANCE", "Scheduled overhaul"
        );

        listener.onAircraftStatusChanged(event);

        AircraftReadModel updated = storage.get("CS-MOD");
        assertEquals("UNDER_MAINTENANCE", updated.getStatus());
        assertEquals("UNDER_MAINTENANCE", updated.getReadinessStatus());
    }

    @Test
    @DisplayName("Should increment pending maintenance count on MaintenanceScheduledEvent")
    void shouldIncrementMaintenanceOnMaintenanceScheduledEvent() {
        AircraftReadModel model = new AircraftReadModel("CS-MNT", "Boeing 737", "AVAILABLE", 50.0, 0);
        storage.put("CS-MNT", model);

        MaintenanceScheduledEvent event = new MaintenanceScheduledEvent("rec-1", "CS-MNT", "2026-11-20T10:00:00", "A-Check");

        listener.onMaintenanceScheduled(event);

        AircraftReadModel updated = storage.get("CS-MNT");
        assertEquals(1, updated.getPendingMaintenanceCount());
        assertEquals("MAINTENANCE_DUE", updated.getReadinessStatus());
    }

    @Test
    @DisplayName("Should decrement pending maintenance count on MaintenanceCompletedEvent")
    void shouldDecrementMaintenanceOnMaintenanceCompletedEvent() {
        AircraftReadModel model = new AircraftReadModel("CS-MNT", "Boeing 737", "AVAILABLE", 50.0, 1);
        storage.put("CS-MNT", model);

        MaintenanceCompletedEvent event = new MaintenanceCompletedEvent("rec-1", "CS-MNT", "2026-11-20T15:00:00", BigDecimal.valueOf(1500.0), "Repaired engine");

        listener.onMaintenanceCompleted(event);

        AircraftReadModel updated = storage.get("CS-MNT");
        assertEquals(0, updated.getPendingMaintenanceCount());
        assertEquals("READY_FOR_FLIGHT", updated.getReadinessStatus());
    }

    @Test
    @DisplayName("Should add flight hours on FlightCompletedEvent")
    void shouldAddFlightHoursOnFlightCompletedEvent() {
        AircraftReadModel model = new AircraftReadModel("CS-FLT", "Airbus A320", "AVAILABLE", 995.0, 0);
        storage.put("CS-FLT", model);

        FlightCompletedEvent event = new FlightCompletedEvent(
                "FL-1234", "CS-FLT", 10.0
        );

        listener.onFlightCompleted(event);

        AircraftReadModel updated = storage.get("CS-FLT");
        assertEquals(1005.0, updated.getTotalOperationalHours());
    }
}
