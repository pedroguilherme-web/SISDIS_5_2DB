package aisafe.aircrafts.cqrs.events;

import aisafe.shared.domain.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AircraftRegisteredEvent extends DomainEvent {

    private final String registrationNumber;
    private final String modelName;
    private final String status;
    private final Integer seatCapacity;
    private final Double range;
    private final List<String> features;

    @JsonCreator
    public AircraftRegisteredEvent(
            @JsonProperty("registrationNumber") String registrationNumber,
            @JsonProperty("modelName") String modelName,
            @JsonProperty("status") String status,
            @JsonProperty("seatCapacity") Integer seatCapacity,
            @JsonProperty("range") Double range,
            @JsonProperty("features") List<String> features) {
        super("AIRCRAFT_REGISTERED");
        this.registrationNumber = registrationNumber;
        this.modelName = modelName;
        this.status = status;
        this.seatCapacity = seatCapacity;
        this.range = range;
        this.features = features != null ? features : List.of();
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getModelName() {
        return modelName;
    }

    public String getStatus() {
        return status;
    }

    public Integer getSeatCapacity() {
        return seatCapacity;
    }

    public Double getRange() {
        return range;
    }

    public List<String> getFeatures() {
        return features;
    }
}
