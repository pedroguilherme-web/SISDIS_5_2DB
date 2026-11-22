package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.events.FlightEvent;
import aisafe.flights.application.ports.FlightEventPublisher;
import aisafe.flights.application.ports.FlightPeerClient;
import aisafe.flights.domain.FlightNotFoundException;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.application.UseCase;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

@UseCase
@RequiredArgsConstructor
public class ChangeFlightStatusUseCase {
    private final ScheduledFlightRepository repository;
    private final FlightPeerClient peers;
    private final FlightEventPublisher events;

    public FlightResponse execute(String flightId, FlightStatus newStatus, QueryScope scope) {
        Optional<ScheduledFlight> local = repository.findById(flightId);
        if (local.isPresent()) {
            ScheduledFlight flight = local.get();
            FlightStatus previousStatus = flight.getStatus();
            flight.changeStatus(newStatus);
            ScheduledFlight saved = repository.save(flight);
            events.publish(FlightEvent.statusChanged(saved, previousStatus));
            return FlightResponse.from(saved);
        }

        if (scope.includesPeers()) {
            return peers.changeStatus(flightId, newStatus)
                    .orElseThrow(() -> new FlightNotFoundException(flightId));
        }
        throw new FlightNotFoundException(flightId);
    }
}
