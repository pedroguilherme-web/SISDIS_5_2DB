package aisafe.flights.infrastructure;

import aisafe.flights.application.ChangeFlightStatusUseCase;
import aisafe.flights.application.ExternalServiceUnavailableException;
import aisafe.flights.application.ImportFlightsUseCase;
import aisafe.flights.application.QueryScope;
import aisafe.flights.application.ScheduleFlightUseCase;
import aisafe.flights.application.ViewScheduledFlightsByAircraftUseCase;
import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.flights.domain.AircraftUnavailableException;
import aisafe.flights.domain.FlightNotFoundException;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.InvalidFlightStatusTransitionException;
import aisafe.shared.application.dtos.BulkImportResult;
import aisafe.shared.infrastructure.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FlightControllerTest {
    private static final OffsetDateTime DEPARTURE = OffsetDateTime.of(2026, 12, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock private ScheduleFlightUseCase scheduleFlightUseCase;
    @Mock private ViewScheduledFlightsByAircraftUseCase viewScheduledFlightsByAircraftUseCase;
    @Mock private ChangeFlightStatusUseCase changeFlightStatusUseCase;
    @Mock private ImportFlightsUseCase importFlightsUseCase;

    @InjectMocks
    private FlightController flightController;

    private final FlightResponse flight = new FlightResponse("f-1", "CS-TRP", "A320", "LIS", "OPO",
            DEPARTURE, DEPARTURE.plusHours(2), FlightStatus.SCHEDULED, 0);

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        mockMvc = MockMvcBuilders.standaloneSetup(flightController)
                .setControllerAdvice(new FlightApiExceptionHandler(), new GlobalExceptionHandler())
                .build();
    }

    private String scheduleBody() throws Exception {
        return objectMapper.writeValueAsString(new ScheduleFlightRequest("CS-TRP", "LIS", "OPO", DEPARTURE, DEPARTURE.plusHours(2)));
    }

    @Test
    void shouldScheduleFlightAndReturn201WithLinks() throws Exception {
        when(scheduleFlightUseCase.execute(any(ScheduleFlightRequest.class), eq(FlightStatus.SCHEDULED), eq(QueryScope.CLUSTER))).thenReturn(flight);

        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(scheduleBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("f-1"))
                .andExpect(jsonPath("$.aircraftModel").value("A320"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andExpect(jsonPath("$..href").isNotEmpty());
    }

    @Test
    void shouldReturn400WhenRequestBodyIsIncomplete() throws Exception {
        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content("{\"aircraftId\":\"CS-TRP\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(scheduleFlightUseCase);
    }

    @Test
    void shouldReturn409WhenAircraftIsUnavailable() throws Exception {
        when(scheduleFlightUseCase.execute(any(ScheduleFlightRequest.class), eq(FlightStatus.SCHEDULED), eq(QueryScope.CLUSTER))).thenThrow(new AircraftUnavailableException("CS-TRP"));

        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(scheduleBody()))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn503WithRetryAfterWhenADependencyIsDown() throws Exception {
        when(scheduleFlightUseCase.execute(any(ScheduleFlightRequest.class), eq(FlightStatus.SCHEDULED), eq(QueryScope.CLUSTER)))
                .thenThrow(new ExternalServiceUnavailableException("Aircraft & Maintenance service"));

        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(scheduleBody()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Aircraft & Maintenance service")));
    }

    @Test
    void shouldListFlightsOfTheWholeClusterForAClient() throws Exception {
        when(viewScheduledFlightsByAircraftUseCase.execute("CS-TRP", QueryScope.CLUSTER)).thenReturn(List.of(flight));

        mockMvc.perform(get("/api/flights").param("aircraftId", "CS-TRP"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value("f-1"))
                .andExpect(jsonPath("$.content[0].originIataCode").value("LIS"));
    }

    @Test
    void shouldListOnlyLocalFlightsForAPeer() throws Exception {
        when(viewScheduledFlightsByAircraftUseCase.execute("CS-TRP", QueryScope.LOCAL)).thenReturn(List.of());

        mockMvc.perform(get("/api/flights").param("aircraftId", "CS-TRP").header(PeerForwarding.HEADER, "true"))
                .andExpect(status().isOk());
        verify(viewScheduledFlightsByAircraftUseCase).execute("CS-TRP", QueryScope.LOCAL);
    }

    @Test
    void shouldChangeStatus() throws Exception {
        FlightResponse completed = new FlightResponse("f-1", "CS-TRP", "A320", "LIS", "OPO",
                DEPARTURE, DEPARTURE.plusHours(2), FlightStatus.COMPLETED, 2);
        when(changeFlightStatusUseCase.execute("f-1", FlightStatus.COMPLETED, QueryScope.CLUSTER)).thenReturn(completed);

        mockMvc.perform(patch("/api/flights/f-1/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldReturn409ForAnInvalidTransition() throws Exception {
        when(changeFlightStatusUseCase.execute(eq("f-1"), eq(FlightStatus.SCHEDULED), any()))
                .thenThrow(new InvalidFlightStatusTransitionException(FlightStatus.COMPLETED, FlightStatus.SCHEDULED));

        mockMvc.perform(patch("/api/flights/f-1/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"SCHEDULED\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturn404WhenNoReplicaHasTheFlight() throws Exception {
        when(changeFlightStatusUseCase.execute(eq("nope"), eq(FlightStatus.CANCELED), any()))
                .thenThrow(new FlightNotFoundException("nope"));

        mockMvc.perform(patch("/api/flights/nope/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELED\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldReturn400ForAMissingStatus() throws Exception {
        mockMvc.perform(patch("/api/flights/f-1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(changeFlightStatusUseCase);
    }

    @Test
    void shouldImportFlightsAndReturn201() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "flights.csv", "text/csv", "dummy content".getBytes());
        BulkImportResult<String> mockResult = new BulkImportResult<>();
        mockResult.addSuccess("FL123");
        when(importFlightsUseCase.execute(any())).thenReturn(mockResult);

        mockMvc.perform(multipart("/api/flights/import").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.successfulCount").value(1))
                .andExpect(jsonPath("$.errorCount").value(0));
    }
}
