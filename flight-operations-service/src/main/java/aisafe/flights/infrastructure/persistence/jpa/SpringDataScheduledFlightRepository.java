package aisafe.flights.infrastructure.persistence.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface SpringDataScheduledFlightRepository extends JpaRepository<ScheduledFlightJpaEntity, Long> {

    // NOTA TÉCNICA: Como não existe tabela 'Aircraft' ou 'Model' neste microserviço,
    // mapeamos provisoriamente para 'aircraftRegistrationNumber' no lugar do modelo.
    @Query("SELECT f.aircraftRegistrationNumber AS modelName, COUNT(f.id) AS utilizationValue " +
            "FROM ScheduledFlightJpaEntity f " +
            "WHERE f.status = 'COMPLETED' " +
            "GROUP BY f.aircraftRegistrationNumber " +
            "ORDER BY utilizationValue DESC")
    List<ModelUtilizationProjection> findTopModelsByAssignments(Pageable pageable);

    @Query(value = "SELECT f.aircraft_registration_number AS modelName, " +
            "SUM(TIMESTAMPDIFF(SECOND, f.departure_date_time, f.arrival_date_time)) / 3600.0 AS utilizationValue " +
            "FROM scheduled_flight f " +
            "WHERE f.status = 'COMPLETED' " +
            "GROUP BY f.aircraft_registration_number " +
            "ORDER BY utilizationValue DESC",
            nativeQuery = true)
    List<ModelUtilizationProjection> findTopModelsByFlightHours(Pageable pageable);

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
            "AND f.status = 'COMPLETED' " +
            "AND f.departureDateTime >= :startDate " +
            "AND f.departureDateTime <= :endDate")
    List<ScheduledFlightJpaEntity> findFlightsForUtilization(@Param("registration") String registration, @Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate);

    @Query("SELECT f FROM ScheduledFlightJpaEntity f " +
            "WHERE f.aircraftRegistrationNumber = :registration " +
            "ORDER BY f.departureDateTime ASC")
    List<ScheduledFlightJpaEntity> findByAircraftRegistration(@Param("registration") String registration);

    @Query("SELECT COUNT(f) > 0 FROM ScheduledFlightJpaEntity f " +
            "WHERE f.aircraftRegistrationNumber = :registration " +
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

    @Query("SELECT 0L AS routeId, f.originCode AS originCode, f.destinationCode AS destinationCode, COUNT(f.id) AS flightCount " +
            "FROM ScheduledFlightJpaEntity f " +
            "WHERE f.status = 'COMPLETED' " +
            "AND (cast(:startDate as timestamp) IS NULL OR f.departureDateTime >= :startDate) " +
            "AND (cast(:endDate as timestamp) IS NULL OR f.arrivalDateTime <= :endDate) " +
            "GROUP BY f.originCode, f.destinationCode " +
            "ORDER BY flightCount DESC")
    Page<RouteUtilizationProjection> findFlightUtilizationReports(@Param("startDate") OffsetDateTime startDate, @Param("endDate") OffsetDateTime endDate, Pageable pageable);
}