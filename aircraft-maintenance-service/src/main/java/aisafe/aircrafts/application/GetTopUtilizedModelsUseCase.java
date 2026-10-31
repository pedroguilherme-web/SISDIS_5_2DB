package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.TopUtilizedModelResponse;
import aisafe.aircrafts.domain.FlightServiceClient;
import aisafe.aircrafts.domain.InvalidUtilizationCriteriaException;
import aisafe.aircrafts.domain.TopModelFlightData;
import aisafe.shared.application.UseCase;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Gets the Top 5 most utilized aircraft models based on total flight hours or number of assignments.
 */
@UseCase(readOnly = true)
public class GetTopUtilizedModelsUseCase {

    private final FlightServiceClient flightServiceClient;

    public GetTopUtilizedModelsUseCase(FlightServiceClient flightServiceClient) {
        this.flightServiceClient = flightServiceClient;
    }

    public List<TopUtilizedModelResponse> execute(String criteria) {
        List<TopModelFlightData> data;
        if ("HOURS".equalsIgnoreCase(criteria)) {
            data = flightServiceClient.findTopModelsByFlightHours(5);
        } else if ("ASSIGNMENTS".equalsIgnoreCase(criteria)) {
            data = flightServiceClient.findTopModelsByAssignments(5);
        } else {
            throw new InvalidUtilizationCriteriaException("Invalid criteria. Must be 'HOURS' or 'ASSIGNMENTS'.");
        }

        return data.stream()
                .map(d -> new TopUtilizedModelResponse(d.modelName(), d.value() != null ? d.value() : 0L))
                .collect(Collectors.toList());
    }
}
