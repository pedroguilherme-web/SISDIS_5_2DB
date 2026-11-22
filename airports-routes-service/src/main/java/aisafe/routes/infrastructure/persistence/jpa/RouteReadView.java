package aisafe.routes.infrastructure.persistence.jpa;

import aisafe.routes.infrastructure.messaging.RouteChangedEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "route_read_model")
public class RouteReadView {

    @Id
    @Column(name = "route_key", nullable = false, length = 7)
    private String routeKey;
    @Column(nullable = false, length = 3)
    private String originCode;
    @Column(nullable = false, length = 3)
    private String destinationCode;
    private Integer estimatedFlightTime;
    private Double minimumRange;
    private Integer minimumCapacity;
    private String status;
    @Column(nullable = false)
    private boolean deleted;
    @Column(nullable = false)
    private Instant updatedAt;

    protected RouteReadView() {
    }

    public RouteReadView(String routeKey) {
        this.routeKey = routeKey;
    }

    public void apply(RouteChangedEvent event) {
        if (updatedAt != null && event.occurredAt().isBefore(updatedAt)) {
            return;
        }
        originCode = event.originIataCode();
        destinationCode = event.destinationIataCode();
        estimatedFlightTime = event.estimatedFlightTime();
        minimumRange = event.minimumRange();
        minimumCapacity = event.minimumCapacity();
        status = event.status();
        deleted = "RouteDeleted".equals(event.eventType());
        updatedAt = event.occurredAt();
    }

    public String getRouteKey() { return routeKey; }
    public String getOriginCode() { return originCode; }
    public String getDestinationCode() { return destinationCode; }
    public Integer getEstimatedFlightTime() { return estimatedFlightTime; }
    public Double getMinimumRange() { return minimumRange; }
    public Integer getMinimumCapacity() { return minimumCapacity; }
    public String getStatus() { return status; }
    public boolean isDeleted() { return deleted; }
    public Instant getUpdatedAt() { return updatedAt; }
}
