package aisafe.flights.infrastructure.catalog;

import aisafe.flights.application.dtos.AircraftDTO;
import aisafe.flights.application.dtos.RouteDTO;
import aisafe.flights.infrastructure.persistence.jpa.catalog.AircraftCatalogEntry;
import aisafe.flights.infrastructure.persistence.jpa.catalog.AircraftCatalogRepository;
import aisafe.flights.infrastructure.persistence.jpa.catalog.RouteCatalogEntry;
import aisafe.flights.infrastructure.persistence.jpa.catalog.RouteCatalogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class CatalogUpdater {
    static final String FROM_EVENT = "EVENT";
    static final String FROM_HTTP = "HTTP";

    private final AircraftCatalogRepository aircraft;
    private final RouteCatalogRepository routes;

    public CatalogUpdater(AircraftCatalogRepository aircraft, RouteCatalogRepository routes) {
        this.aircraft = aircraft;
        this.routes = routes;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aircraftChanged(String registration, String model, String status, Integer seatCapacity, Double range) {
        String key = registration.trim().toUpperCase();
        AircraftCatalogEntry entry = aircraft.findById(key).orElseGet(() -> new AircraftCatalogEntry(key));
        if (model != null) entry.setModel(model);
        if (status != null) entry.setStatus(status);
        if (seatCapacity != null) entry.setSeatCapacity(seatCapacity);
        if (range != null) entry.setRangeKm(range);
        entry.setSource(FROM_EVENT);
        entry.setRefreshedAt(Instant.now());
        aircraft.save(entry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void aircraftFetched(AircraftDTO dto, String registration) {
        String key = registration.trim().toUpperCase();
        AircraftCatalogEntry entry = aircraft.findById(key).orElseGet(() -> new AircraftCatalogEntry(key));
        entry.setModel(dto.model());
        entry.setStatus(dto.status());
        entry.setSeatCapacity(dto.seatCapacity());
        entry.setRangeKm(dto.range());
        entry.setSource(FROM_HTTP);
        entry.setRefreshedAt(Instant.now());
        aircraft.save(entry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void routeChanged(String origin, String destination, Double minimumRange, Integer minimumCapacity,
                             String status, boolean deleted) {
        String o = origin.trim().toUpperCase();
        String d = destination.trim().toUpperCase();
        RouteCatalogEntry entry = routes.findById(RouteCatalogEntry.key(o, d)).orElseGet(() -> new RouteCatalogEntry(o, d));
        if (minimumRange != null) entry.setMinimumRange(minimumRange);
        if (minimumCapacity != null) entry.setMinimumCapacity(minimumCapacity);
        if (status != null) entry.setStatus(status);
        entry.setDeleted(deleted);
        entry.setSource(FROM_EVENT);
        entry.setRefreshedAt(Instant.now());
        routes.save(entry);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void routeFetched(RouteDTO dto, String origin, String destination) {
        RouteCatalogEntry entry = routes.findById(RouteCatalogEntry.key(origin, destination))
                .orElseGet(() -> new RouteCatalogEntry(origin, destination));
        entry.setMinimumRange(dto.minimumRange());
        entry.setMinimumCapacity(dto.minimumCapacity());
        entry.setStatus(dto.status());
        entry.setDeleted(false);
        entry.setSource(FROM_HTTP);
        entry.setRefreshedAt(Instant.now());
        routes.save(entry);
    }
}
