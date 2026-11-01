package aisafe.flights.infrastructure;

import aisafe.flights.application.ImportFlightsUseCase;
import aisafe.flights.application.ScheduleFlightUseCase;
import aisafe.flights.application.ViewScheduledFlightsByAircraftUseCase;
import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.flights.domain.AircraftUnavailableException;
import aisafe.flights.domain.FlightStatus;
import aisafe.shared.application.dtos.BulkImportResult;
import aisafe.shared.infrastructure.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FlightControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private ScheduleFlightUseCase scheduleFlightUseCase;

    @Mock
    private ViewScheduledFlightsByAircraftUseCase viewScheduledFlightsByAircraftUseCase;

    @Mock
    private ImportFlightsUseCase importFlightsUseCase;

    @InjectMocks
    private FlightController flightController;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        mockMvc = MockMvcBuilders.standaloneSetup(flightController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void shouldScheduleFlightAndReturn201() throws Exception {
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        OffsetDateTime arrival = departure.plusHours(2);

        ScheduleFlightRequest request = new ScheduleFlightRequest("CS-TRP", "LIS", "OPO", departure, arrival);
        FlightResponse mockResponse = new FlightResponse(1L, "CS-TRP", "LIS", "OPO", departure, arrival, FlightStatus.SCHEDULED);

        Mockito.when(scheduleFlightUseCase.execute(any(ScheduleFlightRequest.class))).thenReturn(mockResponse);

        mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L))
                .andExpect(jsonPath("$.aircraftId").value("CS-TRP"));
    }

    @Test
    void shouldReturn409WhenAircraftIsUnavailable() throws Exception {
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        OffsetDateTime arrival = departure.plusHours(2);

        ScheduleFlightRequest request = new ScheduleFlightRequest("CS-TRP", "LIS", "OPO", departure, arrival);

        Mockito.when(scheduleFlightUseCase.execute(any(ScheduleFlightRequest.class)))
                .thenThrow(new AircraftUnavailableException("CS-TRP"));

        mockMvc.perform(post("/api/flights")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Aircraft CS-TRP is unavailable in the requested timeframe."));
    }

    @Test
    void shouldGetScheduledFlightsByAircraftAndReturn200() throws Exception {
        String aircraftId = "CS-TRP";
        OffsetDateTime departure = OffsetDateTime.now(ZoneOffset.UTC);
        FlightResponse mockResponse = new FlightResponse(1L, aircraftId, "LIS", "OPO", departure, departure.plusHours(2), FlightStatus.SCHEDULED);

        Mockito.when(viewScheduledFlightsByAircraftUseCase.execute(eq(aircraftId))).thenReturn(List.of(mockResponse));

        mockMvc.perform(get("/api/flights").param("aircraftId", aircraftId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].aircraftId").value("CS-TRP"))
                .andExpect(jsonPath("$.content[0].originIataCode").value("LIS"))
                .andExpect(jsonPath("$.content[0].destinationIataCode").value("OPO"));
    }
    @Test
    void shouldImportFlightsAndReturn201() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "flights.csv", "text/csv", "dummy content".getBytes());

        BulkImportResult<String> mockResult = new BulkImportResult<>();
        mockResult.addSuccess("FL123");

        Mockito.when(importFlightsUseCase.execute(any())).thenReturn(mockResult);

        mockMvc.perform(multipart("/api/flights/import").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.successfulCount").value(1))
                .andExpect(jsonPath("$.errorCount").value(0));
    }
}