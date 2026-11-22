package aisafe.aircrafts.cqrs.projections;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AircraftReadModelRepository extends JpaRepository<AircraftReadModel, String> {

    Optional<AircraftReadModel> findByRegistrationNumber(String registrationNumber);
}
