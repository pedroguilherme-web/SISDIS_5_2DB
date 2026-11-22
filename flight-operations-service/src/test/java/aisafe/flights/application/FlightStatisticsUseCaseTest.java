package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightTimelineResponse;
import aisafe.flights.application.dtos.TopModelResponse;
import aisafe.flights.application.ports.FlightPeerClient;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ModelUtilizationData;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FlightStatisticsUseCaseTest {
    private static final OffsetDateTime T0 = OffsetDateTime.of(2026, 12, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    @Mock private ScheduledFlightRepository repository;
    @Mock private FlightPeerClient peers;

    @InjectMocks
    private FlightStatisticsUseCase useCase;

    @Test
    void shouldAddLocalAndPeerHours() {
        when(repository.calculateTotalOperationalHoursByRegistration("CS-TRP")).thenReturn(3.5);
        when(peers.sumTotalHours("CS-TRP")).thenReturn(2.0);

        assertEquals(5.5, useCase.totalHours("cs-trp", QueryScope.CLUSTER), 1e-9);
    }

    @Test
    void shouldUseOnlyLocalHoursWhenAskedByAPeer() {
        when(repository.calculateTotalOperationalHoursByRegistration("CS-TRP")).thenReturn(3.5);

        assertEquals(3.5, useCase.totalHours("CS-TRP", QueryScope.LOCAL), 1e-9);
        verifyNoInteractions(peers);
    }

    @Test
    void shouldAddLocalAndPeerHoursSinceDate() {
        when(repository.calculateOperationalHoursSince("CS-TRP", T0)).thenReturn(1.0);
        when(peers.sumHoursSince("CS-TRP", T0)).thenReturn(4.0);

        assertEquals(5.0, useCase.hoursSince("CS-TRP", T0, QueryScope.CLUSTER), 1e-9);
    }

    @Test
    void shouldNotAskPeersWhenAircraftHasLocalFlights() {
        when(repository.existsByAircraftRegistration("CS-TRP")).thenReturn(true);

        assertTrue(useCase.hasFlights("CS-TRP", QueryScope.CLUSTER));
        verifyNoInteractions(peers);
    }

    @Test
    void shouldAskPeersWhenAircraftHasNoLocalFlights() {
        when(repository.existsByAircraftRegistration("CS-TRP")).thenReturn(false);
        when(peers.anyPeerHasFlights("CS-TRP")).thenReturn(true);

        assertTrue(useCase.hasFlights("CS-TRP", QueryScope.CLUSTER));
    }

    @Test
    void shouldAnswerFalseLocallyWithoutAskingPeers() {
        when(repository.existsByAircraftRegistration("CS-TRP")).thenReturn(false);

        assertFalse(useCase.hasFlights("CS-TRP", QueryScope.LOCAL));
        verify(peers, never()).anyPeerHasFlights(anyString());
    }

    @Test
    void shouldAddRouteCountsAcrossReplicas() {
        when(repository.countByRoute("LIS", "OPO")).thenReturn(3L);
        when(peers.sumCountByRoute("LIS", "OPO")).thenReturn(4L);

        assertEquals(7L, useCase.countByRoute("lis", "opo", QueryScope.CLUSTER));
    }

    @Test
    void shouldMergeTimelinesInChronologicalOrder() {
        ScheduledFlight local = new ScheduledFlight("l", T0.plusDays(1), T0.plusDays(1).plusHours(1),
                FlightStatus.COMPLETED, "OPO", "LIS", "CS-TRP", "A320");
        when(repository.findByAircraftRegistration("CS-TRP")).thenReturn(List.of(local));
        when(peers.timeline("CS-TRP")).thenReturn(List.of(new FlightTimelineResponse(T0, T0.plusHours(1), "LIS", "OPO")));

        List<FlightTimelineResponse> timeline = useCase.timeline("CS-TRP", QueryScope.CLUSTER);

        assertEquals(List.of("LIS", "OPO"), timeline.stream().map(FlightTimelineResponse::origin).toList());
    }

    @Test
    void shouldRankModelsAfterMergingAllReplicasAndRoundForTheClient() {
        when(repository.findModelUtilizationByFlightHours()).thenReturn(List.of(
                new ModelUtilizationData("A320", 10.4),
                new ModelUtilizationData("B737", 8.0)));
        when(peers.modelUtilizationByHours()).thenReturn(List.of(
                new TopModelResponse("A320", 3.3),
                new TopModelResponse("E190", 12.0)));

        List<TopModelResponse> top = useCase.topModelsByHours(2, QueryScope.CLUSTER);

        assertEquals(List.of(new TopModelResponse("A320", 14L), new TopModelResponse("E190", 12L)), top);
    }

    @Test
    void shouldReturnAllUnroundedModelsWhenAskedByAPeer() {
        when(repository.findModelUtilizationByAssignments()).thenReturn(List.of(
                new ModelUtilizationData("A320", 1.0),
                new ModelUtilizationData("B737", 2.0),
                new ModelUtilizationData("E190", 3.0)));

        List<TopModelResponse> top = useCase.topModelsByAssignments(1, QueryScope.LOCAL);

        assertEquals(3, top.size(), "a peer answer is never cut, the limit applies after merging");
        assertEquals("E190", top.getFirst().modelName());
        verifyNoInteractions(peers);
    }

    @Test
    void shouldCheckOverlapOnlyLocally() {
        when(repository.existsByOverlappingSchedule("CS-TRP", T0, T0.plusHours(1))).thenReturn(true);

        assertTrue(useCase.hasOverlappingFlight("cs-trp", T0, T0.plusHours(1)));
        verifyNoInteractions(peers);
    }
}
