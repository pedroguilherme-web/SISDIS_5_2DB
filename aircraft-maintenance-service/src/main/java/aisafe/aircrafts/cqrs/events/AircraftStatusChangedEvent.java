package aisafe.aircrafts.cqrs.events;

import aisafe.shared.domain.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AircraftStatusChangedEvent extends DomainEvent {

    private final String registrationNumber;
    private final String previousStatus;
    private final String newStatus;
    private final String reason;

    @JsonCreator
    public AircraftStatusChangedEvent(
            @JsonProperty("registrationNumber") String registrationNumber,
            @JsonProperty("previousStatus") String previousStatus,
            @JsonProperty("newStatus") String newStatus,
            @JsonProperty("reason") String reason) {
        super("AIRCRAFT_STATUS_CHANGED");
        this.registrationNumber = registrationNumber;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.reason = reason;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getPreviousStatus() {
        return previousStatus;
    }

    public String getNewStatus() {
        return newStatus;
    }

    public String getReason() {
        return reason;
    }
}
