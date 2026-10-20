package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.GetAircraftUtilizationRequest;
import aisafe.aircrafts.application.dtos.UtilizationDataPointResponse;
import aisafe.aircrafts.domain.*;
import aisafe.shared.application.UseCase;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@UseCase(readOnly = true)
public class GetAircraftUtilizationUseCase {

    private final AircraftRepository aircraftRepository;
    private final FlightServiceClient flightServiceClient;

    public GetAircraftUtilizationUseCase(AircraftRepository aircraftRepository, FlightServiceClient flightServiceClient) {
        this.aircraftRepository = aircraftRepository;
        this.flightServiceClient = flightServiceClient;
    }

    public List<UtilizationDataPointResponse> execute(GetAircraftUtilizationRequest request) {
        String registration = request.registration();
        LocalDate startDate = request.startDate();
        LocalDate endDate = request.endDate();
        if (!aircraftRepository.existsByRegistrationNumber(new RegistrationNumber(registration))) {
            throw new AircraftNotFoundException("Aircraft not found.");
        }

        RegistrationNumber reg = new RegistrationNumber(registration);
        List<FlightTimelineData> flights = flightServiceClient.findFlightTimelineByRegistration(reg);

        Map<LocalDate, Double> dailyHours = new TreeMap<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            dailyHours.put(date, 0.0);
        }

        if (flights != null) {
            for (FlightTimelineData flight : flights) {
                if (flight.departureTime() != null && flight.arrivalTime() != null) {
                    LocalDate flightDate = flight.departureTime().toLocalDate();
                    if (dailyHours.containsKey(flightDate)) {
                        double hours = Duration.between(flight.departureTime(), flight.arrivalTime()).toMinutes() / 60.0;
                        dailyHours.put(flightDate, dailyHours.get(flightDate) + hours);
                    }
                }
            }
        }

        return dailyHours.entrySet().stream()
                .map(entry -> new UtilizationDataPointResponse(
                        entry.getKey(),
                        entry.getValue(),
                        (entry.getValue() / 24.0) * 100.0
                ))
                .collect(Collectors.toList());
    }
}
