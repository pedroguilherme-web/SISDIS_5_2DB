package aisafe.flights.infrastructure.readmodel;

import aisafe.flights.application.events.FlightEvent;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.infrastructure.persistence.jpa.readmodel.AircraftActivityEntity;
import aisafe.flights.infrastructure.persistence.jpa.readmodel.AircraftActivityRepository;
import aisafe.flights.infrastructure.persistence.jpa.readmodel.FlightViewEntity;
import aisafe.flights.infrastructure.persistence.jpa.readmodel.FlightViewRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class FlightProjector {
    private final FlightViewRepository flights;
    private final AircraftActivityRepository activities;

    private final AtomicLong sequence = new AtomicLong(-1);

    public FlightProjector(FlightViewRepository flights, AircraftActivityRepository activities) {
        this.flights = flights;
        this.activities = activities;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean apply(FlightEvent event) {
        FlightViewEntity view = flights.findById(event.flightId()).orElseGet(() -> new FlightViewEntity(event.flightId()));
        // Idempotent by revision: duplicated or late events are ignored.
        if (!view.isOlderThan(event)) {
            log.debug("Ignoring {} for flight {} at revision {} (already at {})",
                    event.eventType(), event.flightId(), event.revision(), view.getRevision());
            return false;
        }
        view.apply(event, nextSequence());
        flights.saveAndFlush(view);
        refreshActivity(event.aircraftRegistration(), event.aircraftModel());
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void applyAircraftStatus(String registration, String aircraftModel, String aircraftStatus) {
        AircraftActivityEntity activity = activities.findById(registration)
                .orElseGet(() -> new AircraftActivityEntity(registration));
        if (aircraftModel != null) {
            activity.setAircraftModel(aircraftModel);
        }
        activity.setAircraftStatus(aircraftStatus);
        recomputeMood(activity);
        activities.save(activity);
    }

    private void refreshActivity(String registration, String aircraftModel) {
        AircraftActivityEntity activity = activities.findById(registration)
                .orElseGet(() -> new AircraftActivityEntity(registration));
        if (aircraftModel != null) {
            activity.setAircraftModel(aircraftModel);
        }
        activity.setTotalFlights(flights.countByAircraftRegistration(registration));
        activity.setCompletedFlights(flights.countByAircraftRegistrationAndStatus(registration, FlightStatus.COMPLETED));
        activity.setCanceledFlights(flights.countByAircraftRegistrationAndStatus(registration, FlightStatus.CANCELED));
        activity.setCompletedHours(flights.sumCompletedHours(registration));
        activity.setLatestFlightStatus(flights.findFirstByAircraftRegistrationOrderByProjectionSequenceDesc(registration)
                .map(FlightViewEntity::getStatus)
                .orElse(null));
        recomputeMood(activity);
        activities.save(activity);
    }

    private long nextSequence() {
        sequence.compareAndSet(-1, flights.maxProjectionSequence());
        return sequence.incrementAndGet();
    }

    private static void recomputeMood(AircraftActivityEntity activity) {
        AirplaneMood mood = AirplaneMood.of(activity.getAircraftStatus(), activity.getLatestFlightStatus(),
                activity.getCompletedFlights(), activity.getCompletedHours());
        activity.setMood(mood.mood());
        activity.setMoodReason(mood.reason());
        activity.setSparkleLevel(mood.sparkleLevel());
        activity.setUpdatedAt(Instant.now());
    }
}
