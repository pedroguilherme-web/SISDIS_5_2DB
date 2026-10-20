package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.AircraftOperationalHoursResponse;
import aisafe.aircrafts.application.dtos.CalculateAircraftOperationalHoursRequest;
import aisafe.aircrafts.domain.AircraftNotFoundException;
import aisafe.aircrafts.domain.AircraftRepository;
import aisafe.aircrafts.domain.FlightServiceClient;
import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.shared.application.UseCase;

/**
 * Calculates the total operational hours for a specific aircraft in the fleet.
 * Communicates with FlightServiceClient over HTTP/REST.
 */
@UseCase(readOnly = true)
public class CalculateAircraftOperationalHoursUseCase {

    private final FlightServiceClient flightServiceClient;
    private final AircraftRepository aircraftRepository;

    public CalculateAircraftOperationalHoursUseCase(FlightServiceClient flightServiceClient, 
                                                    AircraftRepository aircraftRepository) {
        this.flightServiceClient = flightServiceClient;
        this.aircraftRepository = aircraftRepository;
    }

    public AircraftOperationalHoursResponse execute(CalculateAircraftOperationalHoursRequest request) {
        RegistrationNumber registrationNumber = request.registrationNumber();
        if (!aircraftRepository.existsByRegistrationNumber(registrationNumber)) {
            throw new AircraftNotFoundException("Aircraft with registration " + registrationNumber.getNumber() + " not found.");
        }

        Double totalHours = flightServiceClient.calculateTotalOperationalHoursByRegistration(registrationNumber);
        if (totalHours == null) {
            totalHours = 0.0;
        }

        return new AircraftOperationalHoursResponse(registrationNumber.getNumber(), totalHours);
    }
}
