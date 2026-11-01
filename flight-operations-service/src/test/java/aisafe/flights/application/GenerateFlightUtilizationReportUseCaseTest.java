package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightUtilizationResponse;
import aisafe.flights.domain.InvalidFlightDateRangeException;
import aisafe.flights.domain.RouteUtilizationData;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.domain.PaginatedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

class GenerateFlightUtilizationReportUseCaseTest {

    private ScheduledFlightRepository repository;
    private GenerateFlightUtilizationReportUseCase useCase;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(ScheduledFlightRepository.class);
        useCase = new GenerateFlightUtilizationReportUseCase(repository);
    }

    @Test
    void shouldThrowExceptionWhenStartDateIsAfterEndDate() {
        OffsetDateTime startDate = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        OffsetDateTime endDate = OffsetDateTime.now(ZoneOffset.UTC);

        assertThrows(InvalidFlightDateRangeException.class, () ->
                useCase.execute(startDate, endDate, 0, 20)
        );
    }

    @Test
    void shouldReturnPaginatedResultSuccessfully() {
        OffsetDateTime startDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(5);
        OffsetDateTime endDate = OffsetDateTime.now(ZoneOffset.UTC);

        List<RouteUtilizationData> mockData = List.of(
                new RouteUtilizationData(1L, "LIS", "OPO", 10L)
        );
        PaginatedResult<RouteUtilizationData> mockResult = new PaginatedResult<>(mockData, 1);

        when(repository.getFlightUtilizationReport(eq(startDate), eq(endDate), eq(0), eq(20)))
                .thenReturn(mockResult);

        PaginatedResult<FlightUtilizationResponse> result = useCase.execute(startDate, endDate, 0, 20);

        assertNotNull(result);
        assertEquals(1, result.totalElements());
        assertEquals("LIS", result.data().get(0).origin());
        assertEquals("OPO", result.data().get(0).destination());
        assertEquals(10L, result.data().get(0).count());
    }
}