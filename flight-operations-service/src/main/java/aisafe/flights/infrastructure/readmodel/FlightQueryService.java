package aisafe.flights.infrastructure.readmodel;

import aisafe.flights.domain.FlightNotFoundException;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.infrastructure.persistence.jpa.readmodel.AircraftActivityRepository;
import aisafe.flights.infrastructure.persistence.jpa.readmodel.FlightViewRepository;
import aisafe.flights.infrastructure.readmodel.ReadModelResponses.AircraftActivity;
import aisafe.flights.infrastructure.readmodel.ReadModelResponses.AirplaneMoodView;
import aisafe.flights.infrastructure.readmodel.ReadModelResponses.FlightView;
import aisafe.flights.infrastructure.readmodel.ReadModelResponses.PageResponse;
import aisafe.shared.domain.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class FlightQueryService {
    private final FlightViewRepository flights;
    private final AircraftActivityRepository activities;

    public FlightQueryService(FlightViewRepository flights, AircraftActivityRepository activities) {
        this.flights = flights;
        this.activities = activities;
    }

    public PageResponse<FlightView> search(String aircraftId, String origin, String destination, String status,
                                           int page, int size) {
        FlightStatus statusFilter = status == null || status.isBlank() ? null : parseStatus(status);
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by("departure").ascending());
        return PageResponse.of(flights.search(upper(aircraftId), upper(origin), upper(destination), statusFilter, pageRequest)
                .map(FlightView::from));
    }

    public FlightView flight(String flightId) {
        return flights.findById(flightId).map(FlightView::from)
                .orElseThrow(() -> new FlightNotFoundException(flightId));
    }

    public AircraftActivity activity(String registration) {
        return activities.findById(registration.trim().toUpperCase()).map(AircraftActivity::from)
                .orElseThrow(() -> new ResourceNotFoundException("No flight activity projected for aircraft " + registration));
    }

    public List<AirplaneMoodView> moods() {
        return activities.findAllByOrderByRegistrationAsc().stream().map(AirplaneMoodView::from).toList();
    }

    public AirplaneMoodView mood(String registration) {
        return activities.findById(registration.trim().toUpperCase()).map(AirplaneMoodView::from)
                .orElseThrow(() -> new ResourceNotFoundException("No mood projected for aircraft " + registration));
    }

    private static String upper(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private static FlightStatus parseStatus(String status) {
        try {
            return FlightStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status '" + status + "'.");
        }
    }
}
