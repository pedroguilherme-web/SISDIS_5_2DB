package aisafe.flights.infrastructure.catalog;

import aisafe.flights.application.dtos.AircraftDTO;
import aisafe.flights.application.ports.AircraftServiceClient;
import aisafe.flights.infrastructure.http.HttpAircraftServiceClient;
import aisafe.flights.infrastructure.persistence.jpa.catalog.AircraftCatalogEntry;
import aisafe.flights.infrastructure.persistence.jpa.catalog.AircraftCatalogRepository;
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
public class CatalogAircraftServiceClient implements AircraftServiceClient {
    private final AircraftCatalogRepository catalog;
    private final CatalogUpdater updater;
    private final HttpAircraftServiceClient http;
    private final Duration maxAge;
    private final Clock clock;

    @Autowired
    public CatalogAircraftServiceClient(AircraftCatalogRepository catalog, CatalogUpdater updater,
                                        HttpAircraftServiceClient http,
                                        @Value("${aisafe.catalog.max-age:PT10M}") Duration maxAge,
                                        @Value("${aisafe.messaging.enabled:false}") boolean messagingEnabled) {
        this(catalog, updater, http, messagingEnabled ? maxAge : Duration.ZERO, Clock.systemUTC());
    }

    CatalogAircraftServiceClient(AircraftCatalogRepository catalog, CatalogUpdater updater,
                                 HttpAircraftServiceClient http, Duration maxAge, Clock clock) {
        this.catalog = catalog;
        this.updater = updater;
        this.http = http;
        this.maxAge = maxAge;
        this.clock = clock;
    }

    @Override
    public Optional<AircraftDTO> findByRegistration(String registration) {
        String key = registration.trim().toUpperCase();
        Optional<AircraftCatalogEntry> cached = catalog.findById(key)
                .filter(entry -> entry.getStatus() != null && isFresh(entry.getRefreshedAt()));
        if (cached.isPresent()) {
            AircraftCatalogEntry entry = cached.get();
            return Optional.of(new AircraftDTO(entry.getRegistration(), entry.getModel(), entry.getStatus(),
                    entry.getSeatCapacity(), entry.getRangeKm()));
        }
        // An old entry is never used when the service is down: scheduling an aircraft that may be under maintenance is worse than a 503.
        Optional<AircraftDTO> remote = http.findByRegistration(key);
        remote.ifPresent(dto -> updater.aircraftFetched(dto, key));
        return remote;
    }

    private boolean isFresh(Instant refreshedAt) {
        return refreshedAt != null && refreshedAt.isAfter(Instant.now(clock).minus(maxAge));
    }
}
