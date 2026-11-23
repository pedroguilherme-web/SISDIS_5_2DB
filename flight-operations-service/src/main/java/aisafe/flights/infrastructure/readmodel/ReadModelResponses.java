package aisafe.flights.infrastructure.readmodel;

import aisafe.flights.domain.FlightStatus;
import aisafe.flights.infrastructure.persistence.jpa.readmodel.AircraftActivityEntity;
import aisafe.flights.infrastructure.persistence.jpa.readmodel.FlightViewEntity;
import org.springframework.data.domain.Page;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public final class ReadModelResponses {
    private ReadModelResponses() {
    }

    public record FlightView(
            String flightId,
            String aircraftRegistration,
            String aircraftModel,
            String origin,
            String destination,
            OffsetDateTime departure,
            OffsetDateTime arrival,
            FlightStatus status,
            double durationHours,
            long revision,
            Instant projectedAt
    ) {
        static FlightView from(FlightViewEntity e) {
            return new FlightView(e.getFlightId(), e.getAircraftRegistration(), e.getAircraftModel(), e.getOrigin(),
                    e.getDestination(), e.getDeparture(), e.getArrival(), e.getStatus(), e.getDurationHours(),
                    e.getRevision(), e.getUpdatedAt());
        }
    }

    public record AircraftActivity(
            String registration,
            String aircraftModel,
            String aircraftStatus,
            long totalFlights,
            long completedFlights,
            long canceledFlights,
            double completedHours,
            FlightStatus latestFlightStatus,
            Instant projectedAt
    ) {
        static AircraftActivity from(AircraftActivityEntity e) {
            return new AircraftActivity(e.getRegistration(), e.getAircraftModel(), e.getAircraftStatus(),
                    e.getTotalFlights(), e.getCompletedFlights(), e.getCanceledFlights(), e.getCompletedHours(),
                    e.getLatestFlightStatus(), e.getUpdatedAt());
        }
    }

    public record AirplaneMoodView(
            String registrationNumber,
            String model,
            String operationalStatus,
            String mood,
            int sparkleLevel,
            String moodReason,
            FlightStatus latestFlightStatus,
            long completedFlights,
            double completedHours,
            Instant lastUpdated
    ) {
        static AirplaneMoodView from(AircraftActivityEntity e) {
            return new AirplaneMoodView(e.getRegistration(), e.getAircraftModel(), e.getAircraftStatus(), e.getMood(),
                    e.getSparkleLevel(), e.getMoodReason(), e.getLatestFlightStatus(), e.getCompletedFlights(),
                    e.getCompletedHours(), e.getUpdatedAt());
        }
    }

    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        static <T> PageResponse<T> of(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }

    public record RebuildResult(int flightsReceived, int flightsApplied) {
    }
}
