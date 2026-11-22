package aisafe.airports.infrastructure.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "airport_read_model")
public class AirportReadView {

    @Id
    @Column(name = "iata_code", length = 3)
    private String iataCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String country;

    private String region;
    private String timezone;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean deleted;

    protected AirportReadView() {
    }

    public AirportReadView(
            String iataCode,
            String name,
            String city,
            String country,
            String region,
            String timezone,
            String status,
            Instant updatedAt) {
        this(iataCode, name, city, country, region, timezone, status, updatedAt, false);
    }

    public AirportReadView(
            String iataCode,
            String name,
            String city,
            String country,
            String region,
            String timezone,
            String status,
            Instant updatedAt,
            boolean deleted) {
        this.iataCode = iataCode;
        this.name = name;
        this.city = city;
        this.country = country;
        this.region = region;
        this.timezone = timezone;
        this.status = status;
        this.updatedAt = updatedAt;
        this.deleted = deleted;
    }

    public void apply(String name, String city, String country, String region,
                      String timezone, String status, Instant updatedAt) {
        apply(name, city, country, region, timezone, status, updatedAt, false);
    }

    public void apply(String name, String city, String country, String region,
                      String timezone, String status, Instant updatedAt, boolean deleted) {
        if (updatedAt.isBefore(this.updatedAt)) {
            return;
        }
        this.name = name;
        this.city = city;
        this.country = country;
        this.region = region;
        this.timezone = timezone;
        this.status = status;
        this.updatedAt = updatedAt;
        this.deleted = deleted;
    }

    public String getIataCode() {
        return iataCode;
    }

    public String getName() {
        return name;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }

    public String getRegion() {
        return region;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getStatus() {
        return status;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }
}
