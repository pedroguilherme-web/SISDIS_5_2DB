package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.TopUtilizedModelResponse;
import aisafe.aircrafts.domain.FlightServiceClient;
import aisafe.aircrafts.domain.TopModelFlightData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GetTopUtilizedModelsUseCaseTest {

    @Mock
    private FlightServiceClient flightServiceClient;

    @InjectMocks
    private GetTopUtilizedModelsUseCase useCase;

    @Test
    void ensureWithAssignmentsReturnsModels() {
        TopModelFlightData data = new TopModelFlightData("A320", 150L);

        when(flightServiceClient.findTopModelsByAssignments(5)).thenReturn(List.of(data));

        List<TopUtilizedModelResponse> result = useCase.execute("ASSIGNMENTS");

        assertEquals(1, result.size());
        assertEquals("A320", result.get(0).modelName());
        assertEquals(150L, result.get(0).utilizationValue());
    }

    @Test
    void ensureWithHoursReturnsModels() {
        TopModelFlightData data = new TopModelFlightData("A320", 5000L);

        when(flightServiceClient.findTopModelsByFlightHours(5)).thenReturn(List.of(data));

        List<TopUtilizedModelResponse> result = useCase.execute("HOURS");

        assertEquals(1, result.size());
        assertEquals("A320", result.get(0).modelName());
        assertEquals(5000L, result.get(0).utilizationValue());
    }

    @Test
    void ensureWithInvalidCriteriaThrowsException() {
        assertThrows(aisafe.shared.domain.DomainException.class, () -> useCase.execute("INVALID"));
    }
}
