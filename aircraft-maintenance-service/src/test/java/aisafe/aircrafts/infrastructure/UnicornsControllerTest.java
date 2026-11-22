package aisafe.aircrafts.infrastructure;

import aisafe.aircrafts.cqrs.projections.AircraftReadModel;
import aisafe.aircrafts.cqrs.projections.AircraftReadModelRepository;
import aisafe.aircrafts.domain.AircraftRepository;
import aisafe.security.application.JwtService;
import aisafe.security.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UnicornsController.class)
@AutoConfigureMockMvc(addFilters = false)
@DisplayName("UnicornsController Integration Tests (Assignment 2 Mock Endpoint)")
class UnicornsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AircraftReadModelRepository readModelRepository;

    @MockitoBean
    private AircraftRepository aircraftRepository;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    @DisplayName("GET /api/v2/unicorns - should return mood list for all airplanes")
    void shouldReturnMoodListForAllAirplanes() throws Exception {
        AircraftReadModel a1 = new AircraftReadModel("CS-TKA", "Airbus A320neo", "AVAILABLE", 50.0, 0);
        AircraftReadModel a2 = new AircraftReadModel("CS-TKB", "Boeing 737 MAX", "UNDER_MAINTENANCE", 120.0, 1);

        when(readModelRepository.count()).thenReturn(2L);
        when(readModelRepository.findAll()).thenReturn(List.of(a1, a2));

        mockMvc.perform(get("/api/v2/unicorns")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].registrationNumber").value("CS-TKA"))
                .andExpect(jsonPath("$[0].mood").value("HAPPY"))
                .andExpect(jsonPath("$[0].sparkleLevel").value(98))
                .andExpect(jsonPath("$[1].registrationNumber").value("CS-TKB"))
                .andExpect(jsonPath("$[1].mood").value("UNDER_CARE"))
                .andExpect(jsonPath("$[1].sparkleLevel").value(45));
    }

    @Test
    @DisplayName("GET /api/v2/unicorns/{registration} - should return specific airplane mood")
    void shouldReturnSpecificAirplaneMood() throws Exception {
        AircraftReadModel a1 = new AircraftReadModel("CS-TKA", "Airbus A320neo", "AVAILABLE", 50.0, 0);

        when(readModelRepository.count()).thenReturn(1L);
        when(readModelRepository.findByRegistrationNumber("CS-TKA")).thenReturn(Optional.of(a1));

        mockMvc.perform(get("/api/v2/unicorns/CS-TKA")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.registrationNumber").value("CS-TKA"))
                .andExpect(jsonPath("$.mood").value("HAPPY"))
                .andExpect(jsonPath("$.sparkleLevel").value(98));
    }

    @Test
    @DisplayName("GET /api/v2/unicorns/{registration} - should return 404 when airplane not found")
    void shouldReturn404WhenNotFound() throws Exception {
        when(readModelRepository.count()).thenReturn(1L);
        when(readModelRepository.findByRegistrationNumber("NON-EXISTENT")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v2/unicorns/NON-EXISTENT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
