package aisafe.flights.infrastructure.persistence.jpa.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteCatalogRepository extends JpaRepository<RouteCatalogEntry, String> {
}
