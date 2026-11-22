package aisafe.routes.infrastructure.persistence.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteReadViewRepository extends JpaRepository<RouteReadView, String> {
    @Query("""
            select route from RouteReadView route
            where route.deleted = false
              and (:origin is null or route.originCode = :origin)
              and (:destination is null or route.destinationCode = :destination)
              and (:status is null or route.status = :status)
            """)
    Page<RouteReadView> search(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("status") String status,
            Pageable pageable);
}
