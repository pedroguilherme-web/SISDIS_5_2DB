package aisafe.flights.domain;

import aisafe.shared.domain.BaseRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduledFlightRepository extends BaseRepository<ScheduledFlight> {
    Optional<ScheduledFlight> findById(String id);

    void lockAircraftForScheduling(String registration);

    List<ScheduledFlight> findByAircraftRegistration(String registration);

    boolean existsByOverlappingSchedule(String registration, OffsetDateTime departureDateTime, OffsetDateTime arrivalDateTime);

    boolean existsByAircraftRegistration(String registration);

    long countByRoute(String origin, String destination);

    List<ModelUtilizationData> findModelUtilizationByFlightHours();

    List<ModelUtilizationData> findModelUtilizationByAssignments();

    double calculateTotalOperationalHoursByRegistration(String registration);

    double calculateOperationalHoursSince(String registration, OffsetDateTime sinceDate);

    List<RouteUtilizationData> findRouteUtilization(OffsetDateTime startDate, OffsetDateTime endDate);
}
