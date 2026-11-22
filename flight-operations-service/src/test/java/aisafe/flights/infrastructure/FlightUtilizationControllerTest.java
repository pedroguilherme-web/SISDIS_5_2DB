package aisafe.flights.infrastructure;

import aisafe.flights.application.GenerateFlightUtilizationReportUseCase;
import aisafe.flights.application.QueryScope;
import aisafe.flights.application.dtos.FlightUtilizationResponse;
import aisafe.shared.domain.PaginatedResult;
import aisafe.shared.infrastructure.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FlightUtilizationControllerTest {
    private MockMvc mockMvc;

    @Mock
    private GenerateFlightUtilizationReportUseCase useCase;

    @InjectMocks
    private FlightUtilizationController controller;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldReturnFlightUtilizationReport() throws Exception {
        PaginatedResult<FlightUtilizationResponse> result =
                new PaginatedResult<>(List.of(new FlightUtilizationResponse("LIS", "OPO", 10)), 1);
        when(useCase.execute(isNull(), isNull(), eq(0), eq(20), eq(QueryScope.CLUSTER))).thenReturn(result);

        mockMvc.perform(get("/api/flights/reports/utilization").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].origin").value("LIS"))
                .andExpect(jsonPath("$.content[0].destination").value("OPO"))
                .andExpect(jsonPath("$.content[0].count").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }

    @Test
    void shouldUseLocalScopeWhenCalledByAPeer() throws Exception {
        when(useCase.execute(any(), any(), anyInt(), anyInt(), eq(QueryScope.LOCAL)))
                .thenReturn(new PaginatedResult<>(List.of(), 0));

        mockMvc.perform(get("/api/flights/reports/utilization").header(PeerForwarding.HEADER, "true"))
                .andExpect(status().isOk());
    }
}
