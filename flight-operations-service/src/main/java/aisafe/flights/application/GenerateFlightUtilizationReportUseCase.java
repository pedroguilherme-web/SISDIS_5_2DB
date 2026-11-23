package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightUtilizationResponse;
import aisafe.flights.application.ports.FlightPeerClient;
import aisafe.flights.domain.InvalidFlightDateRangeException;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.application.UseCase;
import aisafe.shared.domain.PaginatedResult;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@UseCase(readOnly = true)
@RequiredArgsConstructor
public class GenerateFlightUtilizationReportUseCase {
    private final ScheduledFlightRepository repository;
    private final FlightPeerClient peers;

    public PaginatedResult<FlightUtilizationResponse> execute(OffsetDateTime startDate, OffsetDateTime endDate,
                                                              Integer page, Integer size, QueryScope scope) {
        int pageNumber = page != null && page >= 0 ? page : 0;
        int pageSize = size != null && size > 0 ? size : 20;

        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidFlightDateRangeException("startDate cannot be after endDate");
        }

        Map<String, FlightUtilizationResponse> byRoute = new LinkedHashMap<>();
        repository.findRouteUtilization(startDate, endDate)
                .forEach(row -> merge(byRoute, new FlightUtilizationResponse(row.origin(), row.destination(), row.count())));
        if (scope.includesPeers()) {
            peers.routeUtilization(startDate, endDate).forEach(row -> merge(byRoute, row));
        }

        List<FlightUtilizationResponse> sorted = byRoute.values().stream()
                .sorted(Comparator.comparingLong(FlightUtilizationResponse::count).reversed()
                        .thenComparing(FlightUtilizationResponse::origin)
                        .thenComparing(FlightUtilizationResponse::destination))
                .toList();

        int from = (int) Math.min((long) pageNumber * pageSize, sorted.size());
        int to = Math.min(from + pageSize, sorted.size());
        return new PaginatedResult<>(sorted.subList(from, to), sorted.size());
    }

    private static void merge(Map<String, FlightUtilizationResponse> byRoute, FlightUtilizationResponse row) {
        byRoute.merge(row.origin() + "->" + row.destination(), row,
                (a, b) -> new FlightUtilizationResponse(a.origin(), a.destination(), a.count() + b.count()));
    }
}
