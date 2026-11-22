package aisafe.aircrafts.cqrs.projections;

import aisafe.aircrafts.cqrs.events.AircraftRegisteredEvent;
import aisafe.aircrafts.cqrs.events.AircraftStatusChangedEvent;
import aisafe.maintenance.cqrs.events.MaintenanceCompletedEvent;
import aisafe.maintenance.cqrs.events.MaintenanceScheduledEvent;
import aisafe.shared.domain.events.FlightCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listener for updating aircraft read projections from domain events.
 */
@Component
public class AircraftProjectionListener {

    private static final Logger log = LoggerFactory.getLogger(AircraftProjectionListener.class);

    private final AircraftReadModelRepository readModelRepository;

    @Autowired
    public AircraftProjectionListener(AircraftReadModelRepository readModelRepository) {
        this.readModelRepository = readModelRepository;
    }

    @EventListener
    @RabbitListener(queues = "aisafe.cqrs.projections.queue")
    @Transactional
    public void onAircraftRegistered(AircraftRegisteredEvent event) {
        log.info("CQRS Projection: Updating read model for registered aircraft {}", event.getRegistrationNumber());
        AircraftReadModel model = readModelRepository.findByRegistrationNumber(event.getRegistrationNumber())
                .orElse(new AircraftReadModel(
                        event.getRegistrationNumber(),
                        event.getModelName(),
                        event.getStatus(),
                        event.getSeatCapacity(),
                        event.getRange()
                ));

        model.setModelName(event.getModelName());
        model.setStatus(event.getStatus());
        model.setSeatCapacity(event.getSeatCapacity());
        model.setRange(event.getRange());
        model.recalculateReadinessStatus();

        readModelRepository.save(model);
    }

    @EventListener
    @RabbitListener(queues = "aisafe.cqrs.projections.queue")
    @Transactional
    public void onAircraftStatusChanged(AircraftStatusChangedEvent event) {
        log.info("CQRS Projection: Status changed for aircraft {} to {}", event.getRegistrationNumber(), event.getNewStatus());
        AircraftReadModel model = readModelRepository.findByRegistrationNumber(event.getRegistrationNumber())
                .orElseGet(() -> new AircraftReadModel(
                        event.getRegistrationNumber(),
                        "Unknown",
                        event.getNewStatus(),
                        0.0,
                        0
                ));
        model.setStatus(event.getNewStatus());
        model.recalculateReadinessStatus();
        readModelRepository.save(model);
    }

    @EventListener
    @RabbitListener(queues = "aisafe.cqrs.projections.queue")
    @Transactional
    public void onMaintenanceScheduled(MaintenanceScheduledEvent event) {
        log.info("CQRS Projection: Maintenance scheduled for aircraft {}", event.getRegistrationNumber());
        AircraftReadModel model = readModelRepository.findByRegistrationNumber(event.getRegistrationNumber())
                .orElseGet(() -> new AircraftReadModel(
                        event.getRegistrationNumber(),
                        "Unknown",
                        "AVAILABLE",
                        0.0,
                        0
                ));
        int current = model.getPendingMaintenanceCount() != null ? model.getPendingMaintenanceCount() : 0;
        model.setPendingMaintenanceCount(current + 1);
        model.recalculateReadinessStatus();
        readModelRepository.save(model);
    }

    @EventListener
    @RabbitListener(queues = "aisafe.cqrs.projections.queue")
    @Transactional
    public void onMaintenanceCompleted(MaintenanceCompletedEvent event) {
        log.info("CQRS Projection: Maintenance completed for aircraft {}", event.getRegistrationNumber());
        AircraftReadModel model = readModelRepository.findByRegistrationNumber(event.getRegistrationNumber())
                .orElseGet(() -> new AircraftReadModel(
                        event.getRegistrationNumber(),
                        "Unknown",
                        "AVAILABLE",
                        0.0,
                        0
                ));
        int current = model.getPendingMaintenanceCount() != null ? model.getPendingMaintenanceCount() : 0;
        model.setPendingMaintenanceCount(Math.max(0, current - 1));
        model.recalculateReadinessStatus();
        readModelRepository.save(model);
    }

    @EventListener
    @RabbitListener(queues = "aisafe.cqrs.projections.queue")
    @Transactional
    public void onFlightCompleted(FlightCompletedEvent event) {
        log.info("CQRS Projection: Flight completed for aircraft {}, adding {} flight hours",
                event.getAircraftRegistration(), event.getFlightDurationHours());
        AircraftReadModel model = readModelRepository.findByRegistrationNumber(event.getAircraftRegistration())
                .orElseGet(() -> new AircraftReadModel(
                        event.getAircraftRegistration(),
                        "Unknown",
                        "AVAILABLE",
                        0.0,
                        0
                ));
        double currentHours = model.getTotalOperationalHours() != null ? model.getTotalOperationalHours() : 0.0;
        model.setTotalOperationalHours(currentHours + event.getFlightDurationHours());
        model.recalculateReadinessStatus();
        readModelRepository.save(model);
    }
}
