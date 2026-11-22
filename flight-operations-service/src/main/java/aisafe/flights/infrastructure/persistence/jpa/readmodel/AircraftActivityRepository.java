package aisafe.flights.infrastructure.persistence.jpa.readmodel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AircraftActivityRepository extends JpaRepository<AircraftActivityEntity, String> {
    List<AircraftActivityEntity> findAllByOrderByRegistrationAsc();
}
