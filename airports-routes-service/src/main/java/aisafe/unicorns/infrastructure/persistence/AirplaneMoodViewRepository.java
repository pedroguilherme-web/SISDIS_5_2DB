package aisafe.unicorns.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AirplaneMoodViewRepository extends JpaRepository<AirplaneMoodView, String> {
    List<AirplaneMoodView> findAllByOrderByRegistrationAsc();
}
