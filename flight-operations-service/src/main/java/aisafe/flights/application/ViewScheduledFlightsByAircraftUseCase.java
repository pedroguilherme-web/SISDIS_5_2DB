package aisafe.flights.application;

import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.ports.AircraftServiceClient;
import aisafe.flights.application.ports.FlightPeerClient;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.application.UseCase;
import aisafe.shared.domain.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@UseCase(readOnly = true)
@RequiredArgsConstructor
public class ViewScheduledFlightsByAircraftUseCase {
    private final ScheduledFlightRepository scheduledFlightRepository;
    private final AircraftServiceClient aircraftService;
    private final FlightPeerClient peers;

    public List<FlightResponse> execute(String aircraftId, QueryScope scope) {
        String registration = aircraftId.trim().toUpperCase();

        if (scope.includesPeers()) {
            ensureAircraftExists(registration);
        }

        List<FlightResponse> flights = new ArrayList<>(scheduledFlightRepository.findByAircraftRegistration(registration)
                .stream()
                .map(FlightResponse::from)
                .toList());

        if (scope.includesPeers()) {
            flights.addAll(peers.findFlightsByAircraft(registration));
        }

        flights.sort(Comparator.comparing(FlightResponse::departureDateTime));
        return flights;
    }

    private void ensureAircraftExists(String registration) {
        try {
            if (aircraftService.findByRegistration(registration).isEmpty()) {
                throw new ResourceNotFoundException("Aircraft not found: " + registration);
            }
        } catch (ExternalServiceUnavailableException e) {
            log.warn("Could not verify aircraft {}: {}. Listing flights anyway.", registration, e.getMessage());
        }
    }
}
