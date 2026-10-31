package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.CalculateFuelEfficiencyRequest;
import aisafe.aircrafts.application.dtos.FuelEfficiencyResponse;
import aisafe.aircrafts.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CalculateFuelEfficiencyUseCaseTest {

    @Mock
    private AircraftRepository aircraftRepository;

    @Mock
    private RouteServiceClient routeServiceClient;

    @InjectMocks
    private CalculateFuelEfficiencyUseCase useCase;

    private Aircraft aircraft;
    private RouteData routeData;

    @BeforeEach
    void setUp() {
        AircraftModel model = new AircraftModel("A320", Manufacturer.AIRBUS, 26730.0, 6150.0, 833.0, null, 180);
        aircraft = new Aircraft(AircraftStatus.AVAILABLE, LocalDate.of(2020, 1, 1), model, new RegistrationNumber("CS-TPA"), 150, 5000.0, List.of());
        routeData = new RouteData("OPO", "LIS", 45, 500.0, 100);
    }

    @Test
    void ensureCalculatesOnlyPerAircraftWhenRouteNotProvided() {
        when(aircraftRepository.findByRegistrationNumber(any(RegistrationNumber.class))).thenReturn(Optional.of(aircraft));

        FuelEfficiencyResponse response = useCase.execute(new CalculateFuelEfficiencyRequest("CS-TPA", null, null));

        assertNotNull(response);
        assertEquals("CS-TPA", response.registrationNumber());
        assertEquals(26730.0 / 5000.0, response.fuelConsumptionPerDistanceUnit());
        assertNull(response.routeOrigin());
        assertNull(response.routeDestination());
        assertNull(response.fuelNeededForRoute());
        verify(routeServiceClient, never()).findRouteByOriginAndDestination(any(), any());
    }

    @Test
    void ensureCalculatesPerAircraftAndRouteWhenProvided() {
        when(aircraftRepository.findByRegistrationNumber(any(RegistrationNumber.class))).thenReturn(Optional.of(aircraft));
        when(routeServiceClient.findRouteByOriginAndDestination("OPO", "LIS")).thenReturn(routeData);

        FuelEfficiencyResponse response = useCase.execute(new CalculateFuelEfficiencyRequest("CS-TPA", "OPO", "LIS"));

        assertNotNull(response);
        assertEquals("CS-TPA", response.registrationNumber());
        assertEquals(26730.0 / 5000.0, response.fuelConsumptionPerDistanceUnit());
        assertEquals("OPO", response.routeOrigin());
        assertEquals("LIS", response.routeDestination());
        assertEquals((26730.0 / 5000.0) * 500.0, response.fuelNeededForRoute());
    }

    @Test
    void ensureExceptionWhenAircraftNotFound() {
        when(aircraftRepository.findByRegistrationNumber(any(RegistrationNumber.class))).thenReturn(Optional.empty());

        assertThrows(AircraftNotFoundException.class, () -> useCase.execute(new CalculateFuelEfficiencyRequest("CS-TPA", null, null)));
    }

    @Test
    void ensureCalculatesOnlyPerAircraftWhenRouteNotFound() {
        when(aircraftRepository.findByRegistrationNumber(any(RegistrationNumber.class))).thenReturn(Optional.of(aircraft));
        when(routeServiceClient.findRouteByOriginAndDestination("OPO", "LIS")).thenReturn(null);

        FuelEfficiencyResponse response = useCase.execute(new CalculateFuelEfficiencyRequest("CS-TPA", "OPO", "LIS"));

        assertNotNull(response);
        assertEquals("CS-TPA", response.registrationNumber());
        assertNull(response.fuelNeededForRoute());
    }

    @Test
    void ensureCalculatesOnlyPerAircraftWhenOriginNull() {
        when(aircraftRepository.findByRegistrationNumber(any(RegistrationNumber.class))).thenReturn(Optional.of(aircraft));

        FuelEfficiencyResponse response = useCase.execute(new CalculateFuelEfficiencyRequest("CS-TPA", null, "LIS"));

        assertNotNull(response);
        assertEquals("CS-TPA", response.registrationNumber());
        assertNull(response.fuelNeededForRoute());
        verify(routeServiceClient, never()).findRouteByOriginAndDestination(any(), any());
    }

    @Test
    void ensureCalculatesOnlyPerAircraftWhenDestinationNull() {
        when(aircraftRepository.findByRegistrationNumber(any(RegistrationNumber.class))).thenReturn(Optional.of(aircraft));

        FuelEfficiencyResponse response = useCase.execute(new CalculateFuelEfficiencyRequest("CS-TPA", "OPO", null));

        assertNotNull(response);
        assertEquals("CS-TPA", response.registrationNumber());
        assertNull(response.fuelNeededForRoute());
        verify(routeServiceClient, never()).findRouteByOriginAndDestination(any(), any());
    }
}