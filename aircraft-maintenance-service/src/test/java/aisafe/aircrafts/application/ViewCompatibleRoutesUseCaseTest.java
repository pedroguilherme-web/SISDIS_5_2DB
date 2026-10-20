package aisafe.aircrafts.application;

import aisafe.aircrafts.application.dtos.CompatibleRouteResponse;
import aisafe.aircrafts.domain.*;
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
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ViewCompatibleRoutesUseCaseTest {

    @Mock
    private RouteServiceClient routeServiceClient;

    @Mock
    private AircraftRepository aircraftRepository;

    @InjectMocks
    private ViewCompatibleRoutesUseCase viewCompatibleRoutes;

    private AircraftModel buildModel() {
        return new AircraftModel("A320", Manufacturer.AIRBUS, 26730.0, 6150.0, 833.0, null, 180);
    }

    private Aircraft buildAircraft() {
        return new Aircraft(AircraftStatus.AVAILABLE, LocalDate.of(2020, 1, 1),
                buildModel(), new RegistrationNumber("CS-TPA"), 150, 6000.0, List.of());
    }

    @Test
    void ensureCompatibleRoutesAreReturned() {
        Aircraft aircraft = buildAircraft();
        RegistrationNumber registration = aircraft.getRegistrationNumber();

        CompatibleRouteData route1 = new CompatibleRouteData("LIS", "OPO", 45, 300.0, 100);
        CompatibleRouteData route2 = new CompatibleRouteData("LIS", "MAD", 60, 600.0, 120);

        when(aircraftRepository.findByRegistrationNumber(registration)).thenReturn(Optional.of(aircraft));
        when(routeServiceClient.findCompatibleRoutes(6000.0, 150)).thenReturn(List.of(route1, route2));

        List<CompatibleRouteResponse> response = viewCompatibleRoutes.execute(registration);

        assertNotNull(response);
        assertEquals(2, response.size());
        assertEquals("LIS", response.get(0).originIataCode());
        verify(routeServiceClient, times(1)).findCompatibleRoutes(6000.0, 150);
    }

    @Test
    void ensureExceptionWhenAircraftNotFound() {
        RegistrationNumber registration = new RegistrationNumber("CS-XXX");
        when(aircraftRepository.findByRegistrationNumber(registration)).thenReturn(Optional.empty());

        assertThrows(AircraftNotFoundException.class, () -> viewCompatibleRoutes.execute(registration));
        verify(routeServiceClient, never()).findCompatibleRoutes(anyDouble(), anyInt());
    }
}
