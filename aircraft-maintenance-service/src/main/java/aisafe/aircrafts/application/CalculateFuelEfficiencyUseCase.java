package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.CalculateFuelEfficiencyRequest;
import aisafe.aircrafts.application.dtos.FuelEfficiencyResponse;
import aisafe.aircrafts.domain.*;
import aisafe.shared.application.UseCase;

@UseCase(readOnly = true)
public class CalculateFuelEfficiencyUseCase {

    private final AircraftRepository aircraftRepository;
    private final RouteServiceClient routeServiceClient;

    public CalculateFuelEfficiencyUseCase(AircraftRepository aircraftRepository, RouteServiceClient routeServiceClient) {
        this.aircraftRepository = aircraftRepository;
        this.routeServiceClient = routeServiceClient;
    }

    public FuelEfficiencyResponse execute(CalculateFuelEfficiencyRequest request) {
        String registrationStr = request.registration();
        String origin = request.origin();
        String destination = request.destination();
        RegistrationNumber registration = new RegistrationNumber(registrationStr);
        Aircraft aircraft = aircraftRepository.findByRegistrationNumber(registration)
                .orElseThrow(() -> new AircraftNotFoundException("Aircraft not found with registration: " + registrationStr));

        Double fuelConsumption = aircraft.getFuelConsumptionPerDistanceUnit();
        Double fuelNeeded = null;

        if (origin != null && destination != null) {
            RouteData route = routeServiceClient.findRouteByOriginAndDestination(origin, destination);
            if (route != null) {
                fuelNeeded = fuelConsumption * route.minimumRange();
            }
        }

        return new FuelEfficiencyResponse(
                registrationStr,
                fuelConsumption,
                origin,
                destination,
                fuelNeeded
        );
    }
}
