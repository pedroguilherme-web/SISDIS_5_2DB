package aisafe.flights.infrastructure.catalog;

import aisafe.flights.application.dtos.RouteDTO;
import aisafe.flights.application.ports.RouteServiceClient;
import aisafe.flights.infrastructure.http.HttpRouteServiceClient;
import aisafe.flights.infrastructure.persistence.jpa.catalog.RouteCatalogEntry;
import aisafe.flights.infrastructure.persistence.jpa.catalog.RouteCatalogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Primary
@Component
public class CatalogRouteServiceClient implements RouteServiceClient {
    private final RouteCatalogRepository catalog;
    private final CatalogUpdater updater;
    private final HttpRouteServiceClient http;
    private final Duration maxAge;
    private final Clock clock;

    @Autowired
    public CatalogRouteServiceClient(RouteCatalogRepository catalog, CatalogUpdater updater,
                                     HttpRouteServiceClient http,
                                     @Value("${aisafe.catalog.max-age:PT10M}") Duration maxAge,
                                        @Value("${aisafe.messaging.enabled:false}") boolean messagingEnabled) {
        this(catalog, updater, http, messagingEnabled ? maxAge : Duration.ZERO, Clock.systemUTC());
    }

    CatalogRouteServiceClient(RouteCatalogRepository catalog, CatalogUpdater updater,
                              HttpRouteServiceClient http, Duration maxAge, Clock clock) {
        this.catalog = catalog;
        this.updater = updater;
        this.http = http;
        this.maxAge = maxAge;
        this.clock = clock;
    }

    @Override
    public Optional<RouteDTO> findRoute(String originIataCode, String destinationIataCode) {
        String origin = originIataCode.trim().toUpperCase();
        String destination = destinationIataCode.trim().toUpperCase();

        Optional<RouteCatalogEntry> cached = catalog.findById(RouteCatalogEntry.key(origin, destination))
                .filter(entry -> isFresh(entry.getRefreshedAt()));
        if (cached.isPresent()) {
            RouteCatalogEntry entry = cached.get();
            if (entry.isDeleted()) {
                return Optional.empty();
            }
            return Optional.of(new RouteDTO(origin, destination, entry.getMinimumRange(), entry.getMinimumCapacity(),
                    entry.getStatus()));
        }
        Optional<RouteDTO> remote = http.findRoute(origin, destination);
        remote.ifPresent(dto -> updater.routeFetched(dto, origin, destination));
        return remote;
    }

    private boolean isFresh(Instant refreshedAt) {
        return refreshedAt != null && refreshedAt.isAfter(Instant.now(clock).minus(maxAge));
    }
}
