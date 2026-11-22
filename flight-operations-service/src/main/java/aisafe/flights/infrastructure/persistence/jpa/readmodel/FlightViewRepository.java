package aisafe.flights.infrastructure.persistence.jpa.readmodel;

import aisafe.flights.domain.FlightStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FlightViewRepository extends JpaRepository<FlightViewEntity, String> {
    @Query("SELECT f FROM FlightViewEntity f " +
            "WHERE (:aircraft IS NULL OR f.aircraftRegistration = :aircraft) " +
            "AND (:origin IS NULL OR f.origin = :origin) " +
            "AND (:destination IS NULL OR f.destination = :destination) " +
            "AND (:status IS NULL OR f.status = :status)")
    Page<FlightViewEntity> search(@Param("aircraft") String aircraft,
                                  @Param("origin") String origin,
                                  @Param("destination") String destination,
                                  @Param("status") FlightStatus status,
                                  Pageable pageable);

    long countByAircraftRegistration(String aircraftRegistration);

    long countByAircraftRegistrationAndStatus(String aircraftRegistration, FlightStatus status);

    @Query("SELECT COALESCE(SUM(f.durationHours), 0) FROM FlightViewEntity f " +
            "WHERE f.aircraftRegistration = :aircraft AND f.status = aisafe.flights.domain.FlightStatus.COMPLETED")
    double sumCompletedHours(@Param("aircraft") String aircraft);

    Optional<FlightViewEntity> findFirstByAircraftRegistrationOrderByProjectionSequenceDesc(String aircraftRegistration);

    @Query("SELECT COALESCE(MAX(f.projectionSequence), 0) FROM FlightViewEntity f")
    long maxProjectionSequence();
}
