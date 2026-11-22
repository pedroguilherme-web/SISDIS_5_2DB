package aisafe.airports.infrastructure.persistence.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AirportReadViewRepository
        extends JpaRepository<AirportReadView, String> {

    Page<AirportReadView> findByNameContainingIgnoreCaseAndDeletedFalse(
            String name,
            Pageable pageable);

    Page<AirportReadView> findByCityContainingIgnoreCaseAndDeletedFalse(
            String city,
            Pageable pageable);

    Page<AirportReadView> findByCountryContainingIgnoreCaseAndDeletedFalse(
            String country,
            Pageable pageable);

    Page<AirportReadView> findByDeletedFalse(Pageable pageable);
}
