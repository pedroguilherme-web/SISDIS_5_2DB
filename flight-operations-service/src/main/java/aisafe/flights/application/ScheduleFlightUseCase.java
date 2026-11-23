package aisafe.flights.application;

import aisafe.flights.application.dtos.AircraftDTO;
import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.dtos.RouteDTO;
import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.flights.application.events.FlightEvent;
import aisafe.flights.application.ports.AircraftServiceClient;
import aisafe.flights.application.ports.FlightEventPublisher;
import aisafe.flights.application.ports.FlightOwnership;
import aisafe.flights.application.ports.FlightPeerClient;
import aisafe.flights.application.ports.RouteServiceClient;
import aisafe.flights.domain.AircraftIncompatibilityException;
import aisafe.flights.domain.AircraftUnavailableException;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.InvalidFlightScheduleException;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.application.UseCase;
import aisafe.shared.domain.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@UseCase
@RequiredArgsConstructor
public class ScheduleFlightUseCase {
    private final ScheduledFlightRepository scheduledFlightRepository;
    private final AircraftServiceClient aircraftService;
    private final RouteServiceClient routeService;
    private final FlightPeerClient peers;
    private final FlightEventPublisher events;
    private final FlightOwnership ownership;

    public FlightResponse execute(ScheduleFlightRequest request) {
        return execute(request, FlightStatus.SCHEDULED, QueryScope.CLUSTER);
    }

    public FlightResponse execute(ScheduleFlightRequest request, FlightStatus initialStatus) {
        return execute(request, initialStatus, QueryScope.CLUSTER);
    }

    public FlightResponse execute(ScheduleFlightRequest request, FlightStatus initialStatus, QueryScope scope) {
        String aircraftId = request.aircraftId().trim().toUpperCase();
        String origin = request.originIataCode().trim().toUpperCase();
        String destination = request.destinationIataCode().trim().toUpperCase();

        if (!request.departureDateTime().isBefore(request.arrivalDateTime())) {
            throw new InvalidFlightScheduleException("Departure must be before arrival.");
        }

        // All flights of an aircraft are created on the replica that owns it, so conflicts are checked in one place.
        // If the owner is down this fails with 503: consistency is preferred over availability for this rule.
        if (scope.includesPeers()) {
            Optional<String> owner = ownership.ownerOf(aircraftId);
            if (owner.isPresent()) {
                return peers.scheduleOn(owner.get(), request, initialStatus);
            }
        }
        scheduledFlightRepository.lockAircraftForScheduling(aircraftId);

        RouteDTO route = routeService.findRoute(origin, destination)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + origin + " -> " + destination));
        if (!route.isActive()) {
            throw new InvalidFlightScheduleException(
                    "Route " + origin + " -> " + destination + " is not active (status: " + route.status() + ").");
        }

        AircraftDTO aircraft = aircraftService.findByRegistration(aircraftId)
                .orElseThrow(() -> new ResourceNotFoundException("Aircraft not found: " + aircraftId));

        validateAircraftForRoute(aircraftId, aircraft, route);
        ensureNoScheduleConflict(aircraftId, request, initialStatus);

        ScheduledFlight flight = new ScheduledFlight(
                request.departureDateTime(),
                request.arrivalDateTime(),
                initialStatus,
                origin,
                destination,
                aircraftId,
                aircraft.model()
        );

        ScheduledFlight saved = scheduledFlightRepository.save(flight);
        events.publish(FlightEvent.scheduled(saved));
        return FlightResponse.from(saved);
    }

    private void validateAircraftForRoute(String aircraftId, AircraftDTO aircraft, RouteDTO route) {
        if (aircraft.isOutOfService()) {
            throw new AircraftUnavailableException(aircraftId, aircraft.status());
        }
        if (route.minimumCapacity() != null && aircraft.seatCapacity() != null
                && aircraft.seatCapacity() < route.minimumCapacity()) {
            throw new AircraftIncompatibilityException("Aircraft capacity (" + aircraft.seatCapacity()
                    + ") is less than the route minimum capacity (" + route.minimumCapacity() + ").");
        }
        if (route.minimumRange() != null && aircraft.range() != null
                && aircraft.range() < route.minimumRange()) {
            throw new AircraftIncompatibilityException("Aircraft range (" + aircraft.range()
                    + ") is less than the route minimum range (" + route.minimumRange() + ").");
        }
    }

    private void ensureNoScheduleConflict(String aircraftId, ScheduleFlightRequest request, FlightStatus initialStatus) {
        if (initialStatus == FlightStatus.CANCELED) {
            return;
        }
        boolean conflict = scheduledFlightRepository.existsByOverlappingSchedule(
                aircraftId, request.departureDateTime(), request.arrivalDateTime())
                || peers.anyPeerHasOverlappingFlight(aircraftId, request.departureDateTime(), request.arrivalDateTime());
        if (conflict) {
            throw new AircraftUnavailableException(aircraftId);
        }
    }
}
