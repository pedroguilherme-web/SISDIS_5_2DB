package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightTimelineResponse;
import aisafe.flights.application.dtos.TopModelResponse;
import aisafe.flights.application.ports.FlightPeerClient;
import aisafe.flights.domain.ModelUtilizationData;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.application.UseCase;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@UseCase(readOnly = true)
@RequiredArgsConstructor
public class FlightStatisticsUseCase {
    private final ScheduledFlightRepository repository;
    private final FlightPeerClient peers;

    public double totalHours(String registration, QueryScope scope) {
        String reg = normalize(registration);
        double local = repository.calculateTotalOperationalHoursByRegistration(reg);
        return scope.includesPeers() ? local + peers.sumTotalHours(reg) : local;
    }

    public double hoursSince(String registration, OffsetDateTime since, QueryScope scope) {
        String reg = normalize(registration);
        double local = repository.calculateOperationalHoursSince(reg, since);
        return scope.includesPeers() ? local + peers.sumHoursSince(reg, since) : local;
    }

    public boolean hasFlights(String registration, QueryScope scope) {
        String reg = normalize(registration);
        return repository.existsByAircraftRegistration(reg)
                || (scope.includesPeers() && peers.anyPeerHasFlights(reg));
    }

    public boolean hasOverlappingFlight(String registration, OffsetDateTime departure, OffsetDateTime arrival) {
        return repository.existsByOverlappingSchedule(normalize(registration), departure, arrival);
    }

    public long countByRoute(String origin, String destination, QueryScope scope) {
        String o = normalize(origin);
        String d = normalize(destination);
        long local = repository.countByRoute(o, d);
        return scope.includesPeers() ? local + peers.sumCountByRoute(o, d) : local;
    }

    public List<FlightTimelineResponse> timeline(String registration, QueryScope scope) {
        String reg = normalize(registration);
        List<FlightTimelineResponse> timeline = new ArrayList<>(repository.findByAircraftRegistration(reg).stream()
                .map(FlightTimelineResponse::from)
                .toList());
        if (scope.includesPeers()) {
            timeline.addAll(peers.timeline(reg));
        }
        timeline.sort(Comparator.comparing(FlightTimelineResponse::departureTime));
        return timeline;
    }

    public List<TopModelResponse> topModelsByHours(int limit, QueryScope scope) {
        List<TopModelResponse> peerRows = scope.includesPeers() ? peers.modelUtilizationByHours() : List.of();
        return rank(repository.findModelUtilizationByFlightHours(), peerRows, limit, scope);
    }

    public List<TopModelResponse> topModelsByAssignments(int limit, QueryScope scope) {
        List<TopModelResponse> peerRows = scope.includesPeers() ? peers.modelUtilizationByAssignments() : List.of();
        return rank(repository.findModelUtilizationByAssignments(), peerRows, limit, scope);
    }

    private static List<TopModelResponse> rank(List<ModelUtilizationData> localRows, List<TopModelResponse> peerRows,
                                               int limit, QueryScope scope) {
        Map<String, Double> totals = new LinkedHashMap<>();
        localRows.forEach(row -> totals.merge(row.modelName(), row.value(), Double::sum));
        peerRows.forEach(row -> totals.merge(row.modelName(), row.valueAsDouble(), Double::sum));

        var sorted = totals.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(e -> new TopModelResponse(e.getKey(), e.getValue()));

        if (!scope.includesPeers()) {
            return sorted.toList();
        }
        return sorted.limit(Math.max(limit, 0)).map(TopModelResponse::rounded).toList();
    }

    private static String normalize(String value) {
        return value.trim().toUpperCase();
    }
}
