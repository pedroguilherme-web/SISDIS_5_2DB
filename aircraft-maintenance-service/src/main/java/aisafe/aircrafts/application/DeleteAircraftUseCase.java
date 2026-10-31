package aisafe.aircrafts.application;

import aisafe.aircrafts.domain.Aircraft;
import aisafe.aircrafts.domain.AircraftNotFoundException;
import aisafe.aircrafts.domain.AircraftRepository;
import aisafe.aircrafts.domain.FlightServiceClient;
import aisafe.aircrafts.domain.RegistrationNumber;
import aisafe.maintenance.domain.MaintenanceRecordRepository;
import aisafe.shared.application.UseCase;
import aisafe.shared.domain.ResourceInUseException;

/**
 * Use case for deleting an existing aircraft from the system.
 */
@UseCase
public class DeleteAircraftUseCase {
    private final AircraftRepository aircraftRepository;
    private final MaintenanceRecordRepository recordRepository;
    private final FlightServiceClient flightServiceClient;

    public DeleteAircraftUseCase(AircraftRepository aircraftRepository, 
                                 MaintenanceRecordRepository recordRepository,
                                 FlightServiceClient flightServiceClient) {
        this.aircraftRepository = aircraftRepository;
        this.recordRepository = recordRepository;
        this.flightServiceClient = flightServiceClient;
    }

    /**
     * Deletes the aircraft with the specified registration number from the system.
     * @param registration The registration number of the aircraft to delete.
     */
    public void execute(RegistrationNumber registration) {
        Aircraft aircraft = aircraftRepository.findByRegistrationNumber(registration)
                .orElseThrow(() -> new AircraftNotFoundException("Aircraft not found with registration: " + registration.getNumber()));

        if (flightServiceClient.existsByAircraftRegistration(registration)) {
            throw new ResourceInUseException("Cannot delete aircraft because it is assigned to scheduled flights.");
        }

        if (recordRepository.existsByAircraftRegistration(registration)) {
            throw new ResourceInUseException("Cannot delete aircraft because it has maintenance records.");
        }

        aircraftRepository.delete(aircraft);
    }
}
