package aisafe.maintenance.cqrs.events;

import aisafe.shared.domain.events.DomainEvent;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class MaintenanceCompletedEvent extends DomainEvent {

    private final String recordId;
    private final String registrationNumber;
    private final String completedDate;
    private final BigDecimal cost;
    private final String notes;

    @JsonCreator
    public MaintenanceCompletedEvent(
            @JsonProperty("recordId") String recordId,
            @JsonProperty("registrationNumber") String registrationNumber,
            @JsonProperty("completedDate") String completedDate,
            @JsonProperty("cost") BigDecimal cost,
            @JsonProperty("notes") String notes) {
        super("MAINTENANCE_COMPLETED");
        this.recordId = recordId;
        this.registrationNumber = registrationNumber;
        this.completedDate = completedDate;
        this.cost = cost;
        this.notes = notes;
    }

    public String getRecordId() {
        return recordId;
    }

    public String getRegistrationNumber() {
        return registrationNumber;
    }

    public String getCompletedDate() {
        return completedDate;
    }

    public BigDecimal getCost() {
        return cost;
    }

    public String getNotes() {
        return notes;
    }
}
