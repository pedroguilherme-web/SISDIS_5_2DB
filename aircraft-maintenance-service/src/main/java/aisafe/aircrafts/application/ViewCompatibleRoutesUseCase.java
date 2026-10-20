package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.CompatibleRouteResponse;
import aisafe.aircrafts.domain.*;
import aisafe.shared.application.UseCase;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Views which routes are compatible with a specific aircraft based on its range and capacity.
 * Interacts with RouteServiceClient over HTTP/REST.
 */
@UseCase(readOnly = true)
public class ViewCompatibleRoutesUseCase {
    private final RouteServiceClient routeServiceClient;
    private final AircraftRepository aircraftRepository;

    public ViewCompatibleRoutesUseCase(RouteServiceClient routeServiceClient, AircraftRepository aircraftRepository) {
        this.routeServiceClient = routeServiceClient;
        this.aircraftRepository = aircraftRepository;
    }

    public List<CompatibleRouteResponse> execute(RegistrationNumber registrationNumber) {
        Aircraft aircraft = aircraftRepository.findByRegistrationNumber(registrationNumber)
                .orElseThrow(() -> new AircraftNotFoundException("Aircraft with registration " + registrationNumber + " not found."));

        return routeServiceClient.findCompatibleRoutes(aircraft.getRange(), aircraft.getSeatCapacity())
                .stream()
                .map(route -> new CompatibleRouteResponse(
                        route.origin(),
                        route.destination(),
                        route.estimatedFlightTime(),
                        route.minimumRange(),
                        route.minimumCapacity()
                ))
                .collect(Collectors.toList());
    }
}
