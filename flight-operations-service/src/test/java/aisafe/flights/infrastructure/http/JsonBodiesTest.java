package aisafe.flights.infrastructure.http;

import aisafe.flights.application.dtos.FlightUtilizationResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonBodiesTest {
    private static final FlightUtilizationResponse ROW = new FlightUtilizationResponse("LIS", "OPO", 2);

    @Test
    void shouldReadAPlainArray() {
        assertEquals(List.of(ROW), JsonBodies.readList("[{\"origin\":\"LIS\",\"destination\":\"OPO\",\"count\":2}]", FlightUtilizationResponse.class));
    }

    @Test
    void shouldReadAHalCollection() {
        String hal = "{\"_embedded\":{\"anyNameList\":[{\"origin\":\"LIS\",\"destination\":\"OPO\",\"count\":2,\"_links\":{}}]},\"_links\":{}}";
        assertEquals(List.of(ROW), JsonBodies.readList(hal, FlightUtilizationResponse.class));
    }

    @Test
    void shouldReadPageWrappers() {
        assertEquals(List.of(ROW), JsonBodies.readList("{\"content\":[{\"origin\":\"LIS\",\"destination\":\"OPO\",\"count\":2}]}", FlightUtilizationResponse.class));
        assertEquals(List.of(ROW), JsonBodies.readList("{\"data\":[{\"origin\":\"LIS\",\"destination\":\"OPO\",\"count\":2}]}", FlightUtilizationResponse.class));
    }

    @Test
    void shouldReturnEmptyListForEmptyBodiesAndEmptyHalCollections() {
        assertTrue(JsonBodies.readList("", FlightUtilizationResponse.class).isEmpty());
        assertTrue(JsonBodies.readList("{\"_links\":{}}", FlightUtilizationResponse.class).isEmpty());
        assertTrue(JsonBodies.readList("{\"_embedded\":{}}", FlightUtilizationResponse.class).isEmpty());
    }

    @Test
    void shouldFailClearlyOnMalformedJson() {
        assertThrows(IllegalStateException.class, () -> JsonBodies.readList("{not json", FlightUtilizationResponse.class));
        assertThrows(IllegalStateException.class, () -> JsonBodies.read("{not json", FlightUtilizationResponse.class));
    }

    @Test
    void shouldExtractTheMessageOfAnErrorBody() {
        assertEquals("Route not found", JsonBodies.errorMessage("{\"message\":\"Route not found\"}"));
        assertEquals("plain text", JsonBodies.errorMessage("plain text"));
        assertEquals("", JsonBodies.errorMessage(null));
    }
}
