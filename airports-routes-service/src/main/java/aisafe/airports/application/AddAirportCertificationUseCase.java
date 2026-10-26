package aisafe.airports.application;

import aisafe.airports.domain.ModelName;
import aisafe.shared.domain.DuplicateResourceException;
import aisafe.shared.application.UseCase;
import aisafe.airports.application.dtos.AddCertificationRequest;
import aisafe.airports.application.dtos.AircraftCertificationResponse;
import aisafe.airports.domain.Airport;
import aisafe.airports.domain.AircraftCertification;
import aisafe.airports.domain.AircraftCertificationRepository;
import aisafe.airports.domain.AirportNotFoundException;
import aisafe.airports.domain.AirportRepository;
import aisafe.airports.domain.IataCode;

@UseCase
public class AddAirportCertificationUseCase {
    private final AirportRepository airportRepository;
    private final AircraftCertificationRepository certificationRepository;
    private final AircraftsServiceClient aircraftsServiceClient;

    public AddAirportCertificationUseCase(AirportRepository airportRepository,
                                          AircraftCertificationRepository certificationRepository,
                                          AircraftsServiceClient aircraftsServiceClient) {
        this.airportRepository = airportRepository;
        this.certificationRepository = certificationRepository;
        this.aircraftsServiceClient = aircraftsServiceClient;
    }

    public AircraftCertificationResponse execute(String iataCodeStr, AddCertificationRequest request) {
        Airport airport = airportRepository.findByIataCode(new IataCode(iataCodeStr))
                .orElseThrow(() -> new AirportNotFoundException(iataCodeStr));

        ModelName modelName = new ModelName(request.aircraftModelName());

        if (!aircraftsServiceClient.existsByModelName(modelName)) {
            throw new IllegalArgumentException("Aircraft model with name '" + modelName.getName() + "' not found in Aircrafts Service.");
        }

        if (certificationRepository.existsByAirportCodeAndAircraftModelName(airport.getIataCode(), modelName)) {
            throw new DuplicateResourceException("Aircraft model '" + modelName.getName() + "' is already certified for airport " + iataCodeStr + ".");
        }

        AircraftCertification certification = new AircraftCertification(airport.getIataCode(), modelName);
        certificationRepository.save(certification);

        return AircraftCertificationResponse.from(certification);
    }
}
