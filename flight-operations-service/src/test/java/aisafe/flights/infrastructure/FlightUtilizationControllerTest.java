package aisafe.flights.infrastructure;

import aisafe.flights.application.GenerateFlightUtilizationReportUseCase;
import aisafe.flights.application.dtos.FlightUtilizationResponse;
import aisafe.shared.domain.PaginatedResult;
import aisafe.shared.infrastructure.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
        List<FlightUtilizationResponse> mockData = List.of(
                new FlightUtilizationResponse(1L, "LIS", "OPO", 10L)
        );
        PaginatedResult<FlightUtilizationResponse> mockResult = new PaginatedResult<>(mockData, 1);

        Mockito.when(useCase.execute(any(), any(), anyInt(), anyInt())).thenReturn(mockResult);

        mockMvc.perform(get("/api/flights/reports/utilization")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].origin").value("LIS"))
                .andExpect(jsonPath("$.content[0].destination").value("OPO"))
                .andExpect(jsonPath("$.content[0].count").value(10))
                .andExpect(jsonPath("$.page.totalElements").value(1));
    }
}