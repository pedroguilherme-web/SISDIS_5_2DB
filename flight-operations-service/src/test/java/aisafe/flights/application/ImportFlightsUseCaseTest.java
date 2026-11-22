package aisafe.flights.application;

import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.flights.domain.AircraftUnavailableException;
import aisafe.flights.domain.FlightStatus;
import aisafe.shared.application.dtos.BulkImportResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ImportFlightsUseCaseTest {
    private static final String HEADER = "flightNumber,routeOrigin,routeDestination,aircraftRegistration,departureDate,arrivalDate,status\n";

    private ScheduleFlightUseCase scheduleFlightUseCase;
    private ImportFlightsUseCase useCase;

    @BeforeEach
    void setUp() {
        scheduleFlightUseCase = Mockito.mock(ScheduleFlightUseCase.class);
        useCase = new ImportFlightsUseCase(scheduleFlightUseCase);
    }

    private static InputStream csv(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldReturnErrorWhenFileIsEmpty() {
        BulkImportResult<String> result = useCase.execute(csv(""));

        assertEquals("CSV file is empty", result.getErrors().getFirst().getErrorMessage());
    }

    @Test
    void shouldReturnErrorWhenRequiredHeadersAreMissing() {
        BulkImportResult<String> result = useCase.execute(csv("flightNumber,routeOrigin\nFL123,LIS"));

        assertTrue(result.getErrors().getFirst().getErrorMessage().contains("Missing required columns"));
        verifyNoInteractions(scheduleFlightUseCase);
    }

    @Test
    void shouldImportValidRowWithItsStatus() {
        BulkImportResult<String> result = useCase.execute(csv(HEADER
                + "FL123,LIS,OPO,CS-TKA,2026-10-31T10:00:00Z,2026-10-31T12:00:00Z,COMPLETED"));

        assertTrue(result.getErrors().isEmpty());
        assertEquals(java.util.List.of("FL123"), result.getSuccessfulImports());
        verify(scheduleFlightUseCase).execute(any(ScheduleFlightRequest.class), eq(FlightStatus.COMPLETED));
    }

    @Test
    void shouldDefaultToScheduledWhenStatusColumnIsMissing() {
        useCase.execute(csv("routeOrigin,routeDestination,aircraftRegistration,departureDate,arrivalDate\n"
                + "LIS,OPO,CS-TKA,2026-10-31T10:00:00Z,2026-10-31T12:00:00Z"));

        verify(scheduleFlightUseCase).execute(any(ScheduleFlightRequest.class), eq(FlightStatus.SCHEDULED));
    }

    @Test
    void shouldReportInvalidRowsAndKeepImportingTheOthers() {
        when(scheduleFlightUseCase.execute(any(ScheduleFlightRequest.class), any(FlightStatus.class)))
                .thenThrow(new AircraftUnavailableException("CS-AAA"))
                .thenReturn(null);

        BulkImportResult<String> result = useCase.execute(csv(HEADER
                + "FL1,LIS,OPO,CS-AAA,2026-10-31T10:00:00Z,2026-10-31T12:00:00Z,SCHEDULED\n"
                + "FL2,LIS,OPO,CS-BBB,not-a-date,2026-10-31T12:00:00Z,SCHEDULED\n"
                + "FL3,LIS,OPO,CS-CCC,2026-10-31T10:00:00Z,2026-10-31T12:00:00Z,LANDED\n"
                + "FL4,LIS,OPO,CS-DDD,2026-10-31T10:00:00Z,2026-10-31T12:00:00Z,SCHEDULED"));

        assertEquals(java.util.List.of("FL4"), result.getSuccessfulImports());
        assertEquals(3, result.getErrors().size());
        assertTrue(result.getErrors().get(0).getErrorMessage().contains("CS-AAA"));
        assertTrue(result.getErrors().get(1).getErrorMessage().contains("departureDate"));
        assertTrue(result.getErrors().get(2).getErrorMessage().contains("LANDED"));
    }
}
