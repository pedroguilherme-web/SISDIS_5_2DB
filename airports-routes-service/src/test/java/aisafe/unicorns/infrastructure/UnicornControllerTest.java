package aisafe.unicorns.infrastructure;

import aisafe.security.application.JwtService;
import aisafe.security.domain.UserRepository;
import aisafe.unicorns.application.AirplaneMoodQueryService;
import aisafe.unicorns.application.AirplaneMoodResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UnicornController.class)
@AutoConfigureMockMvc(addFilters = false)
class UnicornControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AirplaneMoodQueryService queryService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void returnsAirplaneMoodReadModelAsJson() throws Exception {
        when(queryService.findAll()).thenReturn(List.of(
                new AirplaneMoodResponse("DEMO-001", "calm", Instant.parse("2026-11-03T10:00:00Z"))));

        mockMvc.perform(get("/api/v2/unicorns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].registration").value("DEMO-001"))
                .andExpect(jsonPath("$[0].mood").value("calm"))
                .andExpect(jsonPath("$[0].updatedAt").value("2026-11-03T10:00:00Z"));
    }

    @Test
    void returnsEmptyListWhenNoMoodsProjected() throws Exception {
        when(queryService.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/v2/unicorns"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}
