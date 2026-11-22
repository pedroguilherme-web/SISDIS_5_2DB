package aisafe.maintenance.application;

import aisafe.shared.application.UseCase;
import aisafe.shared.domain.ConcurrencyException;
import aisafe.maintenance.application.dtos.MaintenanceRecordResponse;
import aisafe.maintenance.application.dtos.UpdateMaintenanceRecordsRequest;
import aisafe.maintenance.domain.*;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import aisafe.maintenance.cqrs.events.MaintenanceCompletedEvent;
import aisafe.shared.infrastructure.messaging.DomainEventPublisher;
import aisafe.shared.infrastructure.messaging.RabbitMQConfig;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Updates an existing maintenance record with respective status and notes for it
 */
@UseCase
public class UpdateMaintenanceRecordUseCase {
    private final MaintenanceRecordRepository recordRepository;
    private DomainEventPublisher eventPublisher;

    public UpdateMaintenanceRecordUseCase(MaintenanceRecordRepository recordRepository) {
        this.recordRepository = recordRepository;
    }

    @Autowired
    public void setEventPublisher(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Updates the status and notes of an existing maintenance record based on the provided request data and saves it to the repository.
     * @param recordId the UUID of the maintenance record to be updated
     * @param request the request containing the new status and notes for the maintenance record
     * @param clientVersion the version of the maintenance record that the client has, used for optimistic locking
     * @return a response containing information of the updated maintenance record
     */
    public MaintenanceRecordResponse execute(UUID recordId, UpdateMaintenanceRecordsRequest request, Long clientVersion) {
        if (request.status() == null) {
            throw new MaintenanceInvalidFieldException("Status cannot be empty.");
        }

        MaintenanceRecord record = recordRepository.findByRecordId(recordId)
                .orElseThrow(() -> new MaintenanceRecordNotFoundException("Maintenance record with ID " + recordId + " not found."));

        Long currentVersion = recordRepository.findVersionFor(recordId);
        if (!currentVersion.equals(clientVersion)) {
            throw new ConcurrencyException("Maintenance record version mismatch.");
        }

        MaintenanceStatus previousStatus = record.getStatus();
        record.changeStatus(request.status());
        if (request.notes() != null && !request.notes().trim().isEmpty()) {
            record.updateNotes(request.notes());
        }

        recordRepository.save(record);

        if (eventPublisher != null && previousStatus != MaintenanceStatus.COMPLETED && request.status() == MaintenanceStatus.COMPLETED) {
            MaintenanceCompletedEvent event = new MaintenanceCompletedEvent(
                    record.getRecordId().toString(),
                    record.getAircraftRegistration().getNumber(),
                    java.time.LocalDateTime.now().toString(),
                    record.getCost(),
                    record.getNotes()
            );
            eventPublisher.publish(RabbitMQConfig.ROUTING_KEY_MAINTENANCE_COMPLETED, event);
        }

        Long newVersion = recordRepository.findVersionFor(recordId);

        return new MaintenanceRecordResponse(
                record.getRecordId(), record.getDescription(), record.getStartDate(),
                record.getExpectedDuration(), record.getNotes(),
                record.getParts().stream().map(MaintenancePart::getPartNumber).toList(),
                record.getTemplate().getName(),
                record.getStatus().name(), record.getAircraftRegistration().getNumber(),
                newVersion,
                record.getComponents().stream().map(Enum::name).collect(Collectors.toSet()),
                record.getCost()
        );
    }
}
