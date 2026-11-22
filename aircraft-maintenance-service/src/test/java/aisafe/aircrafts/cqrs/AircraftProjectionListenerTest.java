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
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AircraftProjectionListener CQRS Projection Tests")
class AircraftProjectionListenerTest {

    private AircraftReadModelRepository repository;
    private AircraftProjectionListener listener;

    @BeforeEach
    void setUp() {
        repository = mock(AircraftReadModelRepository.class);
        listener = new AircraftProjectionListener(repository);
    }

    @Test
    @DisplayName("Should project new read model on AircraftRegisteredEvent")
    void shouldProjectNewReadModelOnAircraftRegisteredEvent() {
        AircraftRegisteredEvent event = new AircraftRegisteredEvent(
                "CS-NEW", "Airbus A330", "AVAILABLE", 250, 6000.0, List.of("WIFI", "USB")
        );

        listener.onAircraftRegistered(event);

        ArgumentCaptor<AircraftReadModel> captor = ArgumentCaptor.forClass(AircraftReadModel.class);
        verify(repository).save(captor.capture());

        AircraftReadModel saved = captor.getValue();
        assertEquals("CS-NEW", saved.getRegistrationNumber());
        assertEquals("Airbus A330", saved.getModelName());
        assertEquals("HAPPY", saved.getMoodStatus());
    }

    @Test
    @DisplayName("Should update status and recalculate mood on AircraftStatusChangedEvent")
    void shouldUpdateStatusOnAircraftStatusChangedEvent() {
        AircraftReadModel model = new AircraftReadModel("CS-MOD", "Boeing 777", "AVAILABLE", 50.0, 0);
        when(repository.findByRegistrationNumber("CS-MOD")).thenReturn(Optional.of(model));

        AircraftStatusChangedEvent event = new AircraftStatusChangedEvent(
                "CS-MOD", "AVAILABLE", "UNDER_MAINTENANCE", "Scheduled overhaul"
        );

        listener.onAircraftStatusChanged(event);

        verify(repository).save(model);
        assertEquals("UNDER_MAINTENANCE", model.getStatus());
        assertEquals("UNDER_CARE", model.getMoodStatus());
    }

    @Test
    @DisplayName("Should increment pending maintenance count on MaintenanceScheduledEvent")
    void shouldIncrementMaintenanceOnMaintenanceScheduledEvent() {
        AircraftReadModel model = new AircraftReadModel("CS-MNT", "Boeing 737", "AVAILABLE", 50.0, 0);
        when(repository.findByRegistrationNumber("CS-MNT")).thenReturn(Optional.of(model));

        MaintenanceScheduledEvent event = new MaintenanceScheduledEvent("rec-1", "CS-MNT", "2026-11-20T10:00:00", "A-Check");

        listener.onMaintenanceScheduled(event);

        verify(repository).save(model);
        assertEquals(1, model.getPendingMaintenanceCount());
        assertEquals("TIRED", model.getMoodStatus());
    }

    @Test
    @DisplayName("Should decrement pending maintenance count on MaintenanceCompletedEvent")
    void shouldDecrementMaintenanceOnMaintenanceCompletedEvent() {
        AircraftReadModel model = new AircraftReadModel("CS-MNT", "Boeing 737", "AVAILABLE", 50.0, 1);
        when(repository.findByRegistrationNumber("CS-MNT")).thenReturn(Optional.of(model));

        MaintenanceCompletedEvent event = new MaintenanceCompletedEvent("rec-1", "CS-MNT", "2026-11-20T15:00:00", BigDecimal.valueOf(1500.0), "Repaired engine");

        listener.onMaintenanceCompleted(event);

        verify(repository).save(model);
        assertEquals(0, model.getPendingMaintenanceCount());
        assertEquals("HAPPY", model.getMoodStatus());
    }

    @Test
    @DisplayName("Should add flight hours and project PROUD mood on FlightCompletedEvent when hours > 1000")
    void shouldAddFlightHoursAndRecalculateMoodOnFlightCompletedEvent() {
        AircraftReadModel model = new AircraftReadModel("CS-FLT", "Airbus A320", "AVAILABLE", 995.0, 0);
        when(repository.findByRegistrationNumber("CS-FLT")).thenReturn(Optional.of(model));

        FlightCompletedEvent event = new FlightCompletedEvent(
                "FL-1234", "CS-FLT", 10.0
        );

        listener.onFlightCompleted(event);

        verify(repository).save(model);
        assertEquals(1005.0, model.getTotalOperationalHours());
        assertEquals("PROUD", model.getMoodStatus());
        assertEquals(88, model.getSparkleLevel());
    }
}
