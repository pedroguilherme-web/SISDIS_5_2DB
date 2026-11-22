package aisafe.flights.infrastructure;

import aisafe.flights.application.FlightStatisticsUseCase;
import aisafe.flights.application.QueryScope;
import aisafe.flights.application.dtos.FlightTimelineResponse;
import aisafe.flights.application.dtos.TopModelResponse;
import aisafe.shared.infrastructure.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FlightStatisticsControllerTest {
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    private MockMvc mockMvc;

    @Mock
    private FlightStatisticsUseCase statistics;

    @InjectMocks
    private FlightStatisticsController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnTotalHoursAsAPlainNumber() throws Exception {
        when(statistics.totalHours("CS-TRP", QueryScope.CLUSTER)).thenReturn(12.5);

        mockMvc.perform(get("/api/flights/aircraft/CS-TRP/hours"))
                .andExpect(status().isOk())
                .andExpect(content().string("12.5"));
    }

    @Test
    void shouldAnswerLocallyWhenCalledByAPeer() throws Exception {
        when(statistics.totalHours("CS-TRP", QueryScope.LOCAL)).thenReturn(1.0);

        mockMvc.perform(get("/api/flights/aircraft/CS-TRP/hours").header(PeerForwarding.HEADER, "true"))
                .andExpect(content().string("1.0"));
    }

    @Test
    void shouldAcceptLocalDateTimeWithoutOffsetAsSentByTheAircraftService() throws Exception {
        when(statistics.hoursSince("CS-TRP", T0, QueryScope.CLUSTER)).thenReturn(3.0);

        mockMvc.perform(get("/api/flights/aircraft/CS-TRP/hours-since").param("since", "2026-01-01T00:00"))
                .andExpect(status().isOk())
                .andExpect(content().string("3.0"));
    }

    @Test
    void shouldReturn400ForAnInvalidSinceDate() throws Exception {
        mockMvc.perform(get("/api/flights/aircraft/CS-TRP/hours-since").param("since", "yesterday"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnExistsAsABoolean() throws Exception {
        when(statistics.hasFlights("CS-TRP", QueryScope.CLUSTER)).thenReturn(true);

        mockMvc.perform(get("/api/flights/aircraft/CS-TRP/exists"))
                .andExpect(content().string("true"));
    }

    @Test
    void shouldReturnTimelineWithTheFieldNamesTheAircraftServiceExpects() throws Exception {
        when(statistics.timeline("CS-TRP", QueryScope.CLUSTER))
                .thenReturn(List.of(new FlightTimelineResponse(T0, T0.plusHours(1), "LIS", "OPO")));

        mockMvc.perform(get("/api/flights/aircraft/CS-TRP/timeline"))
                .andExpect(jsonPath("$[0].departureTime").exists())
                .andExpect(jsonPath("$[0].arrivalTime").exists())
                .andExpect(jsonPath("$[0].origin").value("LIS"))
                .andExpect(jsonPath("$[0].destination").value("OPO"));
    }

    @Test
    void shouldReturnTopModelsWithModelNameAndValue() throws Exception {
        when(statistics.topModelsByHours(5, QueryScope.CLUSTER)).thenReturn(List.of(new TopModelResponse("A320", 14L)));

        mockMvc.perform(get("/api/flights/top-models/hours"))
                .andExpect(jsonPath("$[0].modelName").value("A320"))
                .andExpect(jsonPath("$[0].value").value(14));
    }

    @Test
    void shouldPassTheRequestedLimitForAssignments() throws Exception {
        when(statistics.topModelsByAssignments(3, QueryScope.CLUSTER)).thenReturn(List.of());

        mockMvc.perform(get("/api/flights/top-models/assignments").param("limit", "3"))
                .andExpect(status().isOk());
        verify(statistics).topModelsByAssignments(3, QueryScope.CLUSTER);
    }

    @Test
    void shouldReturnRouteCountAsAPlainNumber() throws Exception {
        when(statistics.countByRoute("LIS", "OPO", QueryScope.CLUSTER)).thenReturn(7L);

        mockMvc.perform(get("/api/flights/count").param("origin", "LIS").param("destination", "OPO"))
                .andExpect(content().string("7"));
    }

    @Test
    void shouldAnswerConflictChecksFromPeers() throws Exception {
        when(statistics.hasOverlappingFlight("CS-TRP", T0, T0.plusHours(2))).thenReturn(true);

        mockMvc.perform(get("/api/flights/aircraft/CS-TRP/conflicts")
                        .param("departure", "2026-01-01T00:00:00Z")
                        .param("arrival", "2026-01-01T02:00:00Z"))
                .andExpect(content().string("true"));
    }
}
