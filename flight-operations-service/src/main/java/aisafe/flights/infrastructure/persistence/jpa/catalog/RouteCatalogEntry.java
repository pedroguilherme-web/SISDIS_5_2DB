package aisafe.flights.infrastructure.persistence.jpa.catalog;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "route_catalog")
@Getter
@Setter
public class RouteCatalogEntry {
    @Id
    private String routeKey;
    private String origin;
    private String destination;
    private Double minimumRange;
    private Integer minimumCapacity;
    private String status;

    private boolean deleted;
    private String source;
    private Instant refreshedAt;

    protected RouteCatalogEntry() {
    }

    public RouteCatalogEntry(String origin, String destination) {
        this.routeKey = key(origin, destination);
        this.origin = origin;
        this.destination = destination;
    }

    public static String key(String origin, String destination) {
        return origin + "-" + destination;
    }
}
