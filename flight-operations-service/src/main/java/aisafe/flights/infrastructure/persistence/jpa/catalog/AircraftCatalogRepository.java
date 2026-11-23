package aisafe.flights.infrastructure.persistence.jpa.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AircraftCatalogRepository extends JpaRepository<AircraftCatalogEntry, String> {
}
