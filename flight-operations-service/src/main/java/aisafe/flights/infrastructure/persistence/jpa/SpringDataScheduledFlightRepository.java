package aisafe.flights.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface SpringDataScheduledFlightRepository extends JpaRepository<ScheduledFlightJpaEntity, String> {
    @Query("SELECT COALESCE(f.aircraftModel, 'UNKNOWN') AS modelName, COUNT(f.id) AS utilizationValue " +
            "FROM ScheduledFlightJpaEntity f " +
            "WHERE f.status = aisafe.flights.domain.FlightStatus.COMPLETED " +
            "GROUP BY COALESCE(f.aircraftModel, 'UNKNOWN') " +
            "ORDER BY COUNT(f.id) DESC")
    List<ModelUtilizationProjection> findModelUtilizationByAssignments();

    @Query(value = "SELECT COALESCE(f.aircraft_model, 'UNKNOWN') AS modelName, " +
            "SUM(TIMESTAMPDIFF(SECOND, f.departure_date_time, f.arrival_date_time)) / 3600.0 AS utilizationValue " +
            "FROM scheduled_flight f " +
            "WHERE f.status = 'COMPLETED' " +
            "GROUP BY COALESCE(f.aircraft_model, 'UNKNOWN') " +
            "ORDER BY utilizationValue DESC",
            nativeQuery = true)
    List<ModelUtilizationProjection> findModelUtilizationByFlightHours();

    @Query(value = "SELECT COALESCE(SUM(TIMESTAMPDIFF(SECOND, f.departure_date_time, f.arrival_date_time)), 0) / 3600.0 " +
            "FROM scheduled_flight f " +
            "WHERE f.status = 'COMPLETED' AND f.aircraft_registration_number = :registration",
            nativeQuery = true)
    Double calculateTotalOperationalHoursByRegistration(@Param("registration") String registration);

    @Query(value = "SELECT COALESCE(SUM(TIMESTAMPDIFF(SECOND, f.departure_date_time, f.arrival_date_time)), 0) / 3600.0 " +
            "FROM scheduled_flight f " +
            "WHERE f.status = 'COMPLETED' AND f.aircraft_registration_number = :registration " +
            "AND f.departure_date_time >= :sinceDate",
            nativeQuery = true)
    Double calculateOperationalHoursSince(@Param("registration") String registration, @Param("sinceDate") OffsetDateTime sinceDate);

    @Query("SELECT f FROM ScheduledFlightJpaEntity f " +
            "WHERE f.aircraftRegistrationNumber = :registration " +
            "ORDER BY f.departureDateTime ASC")
    List<ScheduledFlightJpaEntity> findByAircraftRegistration(@Param("registration") String registration);

    @Query("SELECT COUNT(f) > 0 FROM ScheduledFlightJpaEntity f " +
            "WHERE f.aircraftRegistrationNumber = :registration " +
            "AND f.status <> aisafe.flights.domain.FlightStatus.CANCELED " +
            "AND f.departureDateTime < :arrivalDateTime " +
            "AND f.arrivalDateTime > :departureDateTime")
    boolean hasOverlappingFlights(@Param("registration") String registration,
                                  @Param("departureDateTime") OffsetDateTime departureDateTime,
                                  @Param("arrivalDateTime") OffsetDateTime arrivalDateTime);

    @Query("SELECT COUNT(f) FROM ScheduledFlightJpaEntity f " +
            "WHERE f.originCode = :originCode " +
            "AND f.destinationCode = :destinationCode")
    long countByRoute(@Param("originCode") String originCode, @Param("destinationCode") String destinationCode);

    boolean existsByAircraftRegistrationNumber(String registration);

    @Query("SELECT f.originCode AS originCode, f.destinationCode AS destinationCode, COUNT(f.id) AS flightCount " +
            "FROM ScheduledFlightJpaEntity f " +
            "WHERE f.status = aisafe.flights.domain.FlightStatus.COMPLETED " +
            "AND (cast(:startDate as timestamp) IS NULL OR f.departureDateTime >= :startDate) " +
            "AND (cast(:endDate as timestamp) IS NULL OR f.arrivalDateTime <= :endDate) " +
            "GROUP BY f.originCode, f.destinationCode " +
            "ORDER BY COUNT(f.id) DESC")
    List<RouteUtilizationProjection> findRouteUtilization(@Param("startDate") OffsetDateTime startDate,
                                                          @Param("endDate") OffsetDateTime endDate);
}
