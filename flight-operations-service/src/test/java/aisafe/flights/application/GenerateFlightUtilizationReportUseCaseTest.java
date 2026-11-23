package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightUtilizationResponse;
import aisafe.flights.application.ports.FlightPeerClient;
import aisafe.flights.domain.InvalidFlightDateRangeException;
import aisafe.flights.domain.RouteUtilizationData;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.domain.PaginatedResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GenerateFlightUtilizationReportUseCaseTest {
    @Mock private ScheduledFlightRepository repository;
    @Mock private FlightPeerClient peers;

    @InjectMocks
    private GenerateFlightUtilizationReportUseCase useCase;

    @Test
    void shouldThrowExceptionWhenStartDateIsAfterEndDate() {
        OffsetDateTime start = OffsetDateTime.now();

        assertThrows(InvalidFlightDateRangeException.class,
                () -> useCase.execute(start, start.minusDays(1), 0, 20, QueryScope.CLUSTER));
        verifyNoInteractions(repository, peers);
    }

    @Test
    void shouldMergeRoutesAcrossReplicasSortAndPaginate() {
        when(repository.findRouteUtilization(null, null)).thenReturn(List.of(
                new RouteUtilizationData("LIS", "OPO", 3),
                new RouteUtilizationData("OPO", "MAD", 1)));
        when(peers.routeUtilization(null, null)).thenReturn(List.of(
                new FlightUtilizationResponse("LIS", "OPO", 2),
                new FlightUtilizationResponse("FAO", "LIS", 4)));

        PaginatedResult<FlightUtilizationResponse> firstPage = useCase.execute(null, null, 0, 2, QueryScope.CLUSTER);

        assertEquals(3, firstPage.totalElements());
        assertEquals(List.of(
                new FlightUtilizationResponse("LIS", "OPO", 5),
                new FlightUtilizationResponse("FAO", "LIS", 4)), firstPage.data());

        PaginatedResult<FlightUtilizationResponse> secondPage = useCase.execute(null, null, 1, 2, QueryScope.CLUSTER);
        assertEquals(List.of(new FlightUtilizationResponse("OPO", "MAD", 1)), secondPage.data());
    }

    @Test
    void shouldUseOnlyLocalDataWhenAskedByAPeerAndApplyDefaults() {
        when(repository.findRouteUtilization(null, null)).thenReturn(List.of(new RouteUtilizationData("LIS", "OPO", 3)));

        PaginatedResult<FlightUtilizationResponse> result = useCase.execute(null, null, null, null, QueryScope.LOCAL);

        assertEquals(1, result.data().size());
        verifyNoInteractions(peers);
    }

    @Test
    void shouldReturnEmptyPageBeyondTheLastOne() {
        when(repository.findRouteUtilization(null, null)).thenReturn(List.of(new RouteUtilizationData("LIS", "OPO", 3)));

        PaginatedResult<FlightUtilizationResponse> result = useCase.execute(null, null, 5, 20, QueryScope.LOCAL);

        assertTrue(result.data().isEmpty());
        assertEquals(1, result.totalElements());
    }
}
