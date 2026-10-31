package aisafe.aircrafts.infrastructure;

import aisafe.aircrafts.application.ViewAircraftModelDetailsUseCase;
import aisafe.aircrafts.application.dtos.AircraftModelResponse;
import aisafe.aircrafts.domain.Manufacturer;
import aisafe.aircrafts.domain.RouteData;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InterServiceCommunicationTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ViewAircraftModelDetailsUseCase viewAircraftModelDetails;

    @BeforeEach
    void setUp() {
        AircraftModelController controller = new AircraftModelController(
                null, null, null, null, viewAircraftModelDetails, null, null, null, null
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void whenAirportsServiceQueriesAircraftModel_shouldReturn200OkWithModelDetails() throws Exception {
        AircraftModelResponse mockModel = new AircraftModelResponse(
                "Airbus A320neo",
                Manufacturer.AIRBUS,
                6500.0,
                26730.0,
                840.0,
                false,
                180
        );
        when(viewAircraftModelDetails.execute("Airbus A320neo")).thenReturn(mockModel);

        mockMvc.perform(get("/api/aircraftModels/Airbus A320neo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelName").value("Airbus A320neo"));
    }

    @Test
    void whenParsingRouteResponseFromPedroService_shouldMapIataCodesCorrectly() throws Exception {
        String pedroRouteJson = """
                {
                    "originIataCode": "OPO",
                    "destinationIataCode": "LIS",
                    "estimatedFlightTime": 50,
                    "minimumRange": 450.0,
                    "minimumCapacity": 120,
                    "status": "ACTIVE",
                    "version": 1
                }
                """;

        RouteData routeData = objectMapper.readValue(pedroRouteJson, RouteData.class);

        assertNotNull(routeData);
        assertEquals("OPO", routeData.origin());
        assertEquals("LIS", routeData.destination());
        assertEquals(50, routeData.estimatedFlightTime());
        assertEquals(450.0, routeData.minimumRange());
        assertEquals(120, routeData.minimumCapacity());
    }
}
