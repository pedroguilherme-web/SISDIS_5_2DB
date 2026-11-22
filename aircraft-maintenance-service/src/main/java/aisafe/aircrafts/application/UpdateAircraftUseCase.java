package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.UpdateAircraftRequest;
import aisafe.aircrafts.application.dtos.AircraftResponse;
import aisafe.aircrafts.domain.*;
import aisafe.shared.application.UseCase;
import aisafe.shared.domain.ConcurrencyException;

/**
 * Use case for updating the details of an existing aircraft in the system.
 */
import aisafe.aircrafts.cqrs.events.AircraftStatusChangedEvent;
import aisafe.shared.infrastructure.messaging.DomainEventPublisher;
import aisafe.shared.infrastructure.messaging.RabbitMQConfig;
import org.springframework.beans.factory.annotation.Autowired;

@UseCase
public class UpdateAircraftUseCase {

    private final AircraftRepository aircraftRepository;
    private final AircraftModelRepository aircraftModelRepository;
    private DomainEventPublisher eventPublisher;

    public UpdateAircraftUseCase(AircraftRepository aircraftRepository, AircraftModelRepository aircraftModelRepository) {
        this.aircraftRepository = aircraftRepository;
        this.aircraftModelRepository = aircraftModelRepository;
    }

    @Autowired
    public void setEventPublisher(DomainEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public AircraftResponse execute(RegistrationNumber registration, UpdateAircraftRequest request, Long clientVersion) {

        Aircraft aircraft = aircraftRepository.findByRegistrationNumber(registration)
                .orElseThrow(() -> new AircraftNotFoundException("Aircraft with registration " + registration.getNumber() + " not found."));

        Long currentVersion = aircraftRepository.findVersionFor(registration);
        if (clientVersion != null && currentVersion != null && !currentVersion.equals(clientVersion)) {
            throw new ConcurrencyException("The aircraft has been updated by another user. Please refresh and try again.");
        }

        AircraftModel targetModel = aircraft.getModel();
        if (request.modelName() != null && !request.modelName().isBlank()) {
            targetModel = aircraftModelRepository.findByModelName(request.modelName())
                    .orElseThrow(() -> new AircraftModelNotFoundException("Aircraft model '" + request.modelName() + "' not found."));
        }

        AircraftStatus targetStatus = aircraft.getStatus();
        if (request.status() != null && !request.status().isBlank()) {
            if (!AircraftStatus.isValid(request.status())) {
                throw new AircraftInvalidFieldException("Invalid status value: " + request.status());
            }
            targetStatus = AircraftStatus.valueOf(request.status().toUpperCase());
        }

        AircraftStatus previousStatus = aircraft.getStatus();

        aircraft.updateDetails(
                targetModel,
                request.manufacturingDate(),
                request.seatCapacity(),
                request.range(),
                request.features(),
                targetStatus
        );

        aircraftRepository.save(aircraft);

        if (eventPublisher != null && previousStatus != targetStatus) {
            AircraftStatusChangedEvent event = new AircraftStatusChangedEvent(
                    aircraft.getRegistrationNumber().getNumber(),
                    previousStatus.name(),
                    targetStatus.name(),
                    "Status updated via UpdateAircraftUseCase"
            );
            eventPublisher.publish(RabbitMQConfig.ROUTING_KEY_AIRCRAFT_STATUS_CHANGED, event);
        }

        Long newVersion = aircraftRepository.findVersionFor(registration);

        return AircraftResponse.from(aircraft, newVersion);
    }
}