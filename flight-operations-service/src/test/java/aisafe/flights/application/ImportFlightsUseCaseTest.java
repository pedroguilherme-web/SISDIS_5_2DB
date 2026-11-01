package aisafe.flights.application;

import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.shared.application.dtos.BulkImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ImportFlightsUseCaseTest {

    private ScheduleFlightUseCase scheduleFlightUseCase;
    private ImportFlightsUseCase useCase;

    @BeforeEach
    void setUp() {
        scheduleFlightUseCase = Mockito.mock(ScheduleFlightUseCase.class);
        useCase = new ImportFlightsUseCase(scheduleFlightUseCase);
    }

    @Test
    void shouldReturnErrorWhenFileIsEmpty() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "test.csv", "text/csv", "".getBytes());

        BulkImportResult<String> result = useCase.execute(emptyFile);

        assertFalse(result.getErrors().isEmpty());
        assertEquals("CSV file is empty", result.getErrors().get(0).getErrorMessage());
    }

    @Test
    void shouldReturnErrorWhenRequiredHeadersAreMissing() {
        String csvContent = "flightNumber,routeOrigin\nFL123,LIS";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        BulkImportResult<String> result = useCase.execute(file);

        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().get(0).getErrorMessage().contains("Missing required columns"));
    }

    @Test
    void shouldImportValidRowSuccessfully() {
        String csvContent = "flightNumber,routeOrigin,routeDestination,aircraftRegistration,departureDate,arrivalDate,status\n" +
                "FL123,LIS,OPO,CS-TKA,2026-10-31T10:00:00Z,2026-10-31T12:00:00Z,SCHEDULED";
        MockMultipartFile file = new MockMultipartFile("file", "test.csv", "text/csv", csvContent.getBytes(StandardCharsets.UTF_8));

        when(scheduleFlightUseCase.execute(any(ScheduleFlightRequest.class))).thenReturn(null);

        BulkImportResult<String> result = useCase.execute(file);

        assertTrue(result.getErrors().isEmpty());
        assertEquals(1, result.getSuccessfulImports().size());
        assertEquals("FL123", result.getSuccessfulImports().get(0));
        verify(scheduleFlightUseCase, times(1)).execute(any(ScheduleFlightRequest.class));
    }
}