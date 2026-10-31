package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.GetAircraftUtilizationRequest;
import aisafe.aircrafts.application.dtos.UtilizationDataPointResponse;
import aisafe.aircrafts.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class GetAircraftUtilizationUseCaseTest {

    private AircraftRepository aircraftRepository;
    private FlightServiceClient flightServiceClient;
    private GetAircraftUtilizationUseCase useCase;

    @BeforeEach
    void setUp() {
        aircraftRepository = Mockito.mock(AircraftRepository.class);
        flightServiceClient = Mockito.mock(FlightServiceClient.class);
        useCase = new GetAircraftUtilizationUseCase(aircraftRepository, flightServiceClient);
    }

    @Test
    void ensureThrowsExceptionWhenAircraftDoesNotExist() {
        when(aircraftRepository.existsByRegistrationNumber(any(RegistrationNumber.class))).thenReturn(false);

        assertThrows(AircraftNotFoundException.class, () ->
                useCase.execute(new GetAircraftUtilizationRequest("XX-XXX", LocalDate.of(2023, 1, 1), LocalDate.of(2023, 1, 2)))
        );
    }

    @Test
    void ensureReturnsDataPointsForValidRange() {
        when(aircraftRepository.existsByRegistrationNumber(any(RegistrationNumber.class))).thenReturn(true);

        LocalDate start = LocalDate.of(2023, 1, 1);
        LocalDate end = LocalDate.of(2023, 1, 2);

        FlightTimelineData flight = new FlightTimelineData(
                OffsetDateTime.of(2023, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2023, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC),
                "OPO",
                "LIS"
        );

        when(flightServiceClient.findFlightTimelineByRegistration(any(RegistrationNumber.class)))
                .thenReturn(List.of(flight));

        List<UtilizationDataPointResponse> result = useCase.execute(new GetAircraftUtilizationRequest("XX-XXX", start, end));

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(LocalDate.of(2023, 1, 1), result.get(0).date());
        assertEquals(2.0, result.get(0).flightHours());
        assertEquals(LocalDate.of(2023, 1, 2), result.get(1).date());
        assertEquals(0.0, result.get(1).flightHours());
    }
}
