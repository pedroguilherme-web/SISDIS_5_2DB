package aisafe.flights.infrastructure.persistence.jpa;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AircraftScheduleLockRepository extends JpaRepository<AircraftScheduleLockEntity, String> {
    @Modifying
    @Query(value = "MERGE INTO aircraft_schedule_lock (registration) KEY (registration) VALUES (:registration)", nativeQuery = true)
    void ensureExists(@Param("registration") String registration);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "10000"))
    @Query("SELECT l FROM AircraftScheduleLockEntity l WHERE l.registration = :registration")
    Optional<AircraftScheduleLockEntity> lockForUpdate(@Param("registration") String registration);
}
