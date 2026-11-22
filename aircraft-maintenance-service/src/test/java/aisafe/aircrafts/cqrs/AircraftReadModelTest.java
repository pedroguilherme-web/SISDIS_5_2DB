package aisafe.aircrafts.cqrs;

import aisafe.aircrafts.cqrs.projections.AircraftReadModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AircraftReadModel Tests")
class AircraftReadModelTest {

    @Test
    @DisplayName("Should project UNDER_MAINTENANCE readiness when aircraft is UNDER_MAINTENANCE")
    void shouldProjectUnderMaintenanceWhenUnderMaintenance() {
        AircraftReadModel model = new AircraftReadModel("CS-TKA", "Airbus A320neo", "UNDER_MAINTENANCE", 150.0, 1);

        assertEquals("UNDER_MAINTENANCE", model.getReadinessStatus());
        assertTrue(model.getOperationalSummary().contains("hangar"));
    }

    @Test
    @DisplayName("Should project GROUNDED readiness when aircraft is INACTIVE")
    void shouldProjectGroundedWhenInactive() {
        AircraftReadModel model = new AircraftReadModel("CS-TKB", "Boeing 737 MAX", "INACTIVE", 200.0, 0);

        assertEquals("GROUNDED", model.getReadinessStatus());
        assertTrue(model.getOperationalSummary().contains("grounded"));
    }

    @Test
    @DisplayName("Should project MAINTENANCE_DUE readiness when pending maintenance count > 0")
    void shouldProjectMaintenanceDueWhenPendingMaintenance() {
        AircraftReadModel model = new AircraftReadModel("CS-TKC", "Embraer E195", "AVAILABLE", 650.0, 1);

        assertEquals("MAINTENANCE_DUE", model.getReadinessStatus());
        assertTrue(model.getOperationalSummary().contains("pending"));
    }

    @Test
    @DisplayName("Should project READY_FOR_FLIGHT readiness when aircraft is AVAILABLE and no maintenance pending")
    void shouldProjectReadyForFlightWhenOptimalConditions() {
        AircraftReadModel model = new AircraftReadModel("CS-TKE", "Airbus A321LR", "AVAILABLE", 100.0, 0);

        assertEquals("READY_FOR_FLIGHT", model.getReadinessStatus());
        assertTrue(model.getOperationalSummary().contains("ready"));
    }
}
