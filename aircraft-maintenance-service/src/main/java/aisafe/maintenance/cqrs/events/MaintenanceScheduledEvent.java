package aisafe.maintenance.cqrs.events;

import aisafe.shared.domain.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class MaintenanceScheduledEvent extends DomainEvent {

    private final String recordId;
    private final String registrationNumber;
    private final String scheduledDate;
    private final String description;

    @JsonCreator
    public MaintenanceScheduledEvent(
            @JsonProperty("recordId") String recordId,
            @JsonProperty("registrationNumber") String registrationNumber,
            @JsonProperty("scheduledDate") String scheduledDate,
            @JsonProperty("description") String description) {
        super("MAINTENANCE_SCHEDULED");
        this.recordId = recordId;
        this.registrationNumber = registrationNumber;
        this.scheduledDate = scheduledDate;
        this.description = description;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getScheduledDate() {
        return scheduledDate;
    }

    public String getDescription() {
        return description;
    }
}
