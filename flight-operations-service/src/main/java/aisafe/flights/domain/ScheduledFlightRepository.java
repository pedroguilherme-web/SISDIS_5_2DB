package aisafe.flights.domain;

import aisafe.shared.domain.BaseRepository;
import aisafe.shared.domain.PaginatedResult;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduledFlightRepository extends BaseRepository<ScheduledFlight> {
    Optional<ScheduledFlight> findById(Long id);

    // Substituído RegistrationNumber por String
    List<ScheduledFlight> findByAircraftRegistration(String registration);

    List<ScheduledFlight> findFlightsForUtilization(String registration, OffsetDateTime start, OffsetDateTime end);

    boolean existsByOverlappingSchedule(String registration, OffsetDateTime departureDateTime, OffsetDateTime arrivalDateTime);

    boolean existsByAircraftRegistration(String registration);

    // Substituído IataCode por String
    long countByRoute(String origin, String destination);

    List<ModelUtilizationData> findTopModelsByFlightHours(int limit);
    List<ModelUtilizationData> findTopModelsByAssignments(int limit);

    Double calculateTotalOperationalHoursByRegistration(String registration);

    PaginatedResult<RouteUtilizationData> getFlightUtilizationReport(OffsetDateTime startDate, OffsetDateTime endDate, int page, int size);

    Double calculateOperationalHoursSince(String registration, OffsetDateTime sinceDate);
}