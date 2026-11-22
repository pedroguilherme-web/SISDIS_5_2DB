package aisafe.aircrafts.cqrs;

import aisafe.aircrafts.cqrs.projections.AircraftReadModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AircraftReadModel CQRS Projection Tests")
class AircraftReadModelTest {

    @Test
    @DisplayName("Should project UNDER_CARE mood when aircraft is UNDER_MAINTENANCE")
    void shouldProjectUnderCareMoodWhenUnderMaintenance() {
        AircraftReadModel model = new AircraftReadModel("CS-TKA", "Airbus A320neo", "UNDER_MAINTENANCE", 150.0, 1);

        assertEquals("UNDER_CARE", model.getMoodStatus());
        assertEquals(45, model.getSparkleLevel());
        assertTrue(model.getMoodReason().contains("hangar"));
    }

    @Test
    @DisplayName("Should project GRUMPY mood when aircraft is INACTIVE")
    void shouldProjectGrumpyMoodWhenInactive() {
        AircraftReadModel model = new AircraftReadModel("CS-TKB", "Boeing 737 MAX", "INACTIVE", 200.0, 0);

        assertEquals("GRUMPY", model.getMoodStatus());
        assertEquals(15, model.getSparkleLevel());
        assertTrue(model.getMoodReason().contains("Grounded"));
    }

    @Test
    @DisplayName("Should project TIRED mood when pending maintenance count > 0")
    void shouldProjectTiredMoodWhenPendingMaintenance() {
        AircraftReadModel model = new AircraftReadModel("CS-TKC", "Embraer E195", "AVAILABLE", 650.0, 1);

        assertEquals("TIRED", model.getMoodStatus());
        assertEquals(60, model.getSparkleLevel());
        assertTrue(model.getMoodReason().contains("inspection is due"));
    }

    @Test
    @DisplayName("Should project PROUD mood when total operational hours > 1000")
    void shouldProjectProudMoodWhenVeteranAircraft() {
        AircraftReadModel model = new AircraftReadModel("CS-TKD", "Airbus A330", "AVAILABLE", 1200.0, 0);

        assertEquals("PROUD", model.getMoodStatus());
        assertEquals(88, model.getSparkleLevel());
        assertTrue(model.getMoodReason().contains("Veteran"));
    }

    @Test
    @DisplayName("Should project HAPPY mood when operational hours are normal and no maintenance pending")
    void shouldProjectHappyMoodWhenOptimalConditions() {
        AircraftReadModel model = new AircraftReadModel("CS-TKE", "Airbus A321LR", "AVAILABLE", 100.0, 0);

        assertEquals("HAPPY", model.getMoodStatus());
        assertEquals(98, model.getSparkleLevel());
        assertTrue(model.getMoodReason().contains("Optimal"));
    }
}
