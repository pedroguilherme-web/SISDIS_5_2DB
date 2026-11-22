package aisafe.routes.application;

import aisafe.routes.infrastructure.persistence.jpa.RouteReadView;
import aisafe.routes.infrastructure.persistence.jpa.RouteReadViewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class RouteReadQueryService {

    private final RouteReadViewRepository repository;

    public RouteReadQueryService(RouteReadViewRepository repository) {
        this.repository = repository;
    }

    public Page<RouteReadView> search(
            String origin,
            String destination,
            String status,
            Pageable pageable) {
        String normalizedOrigin = normalize(origin);
        String normalizedDestination = normalize(destination);
        String normalizedStatus = normalize(status);
        return repository.search(normalizedOrigin, normalizedDestination, normalizedStatus, pageable);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }
}
