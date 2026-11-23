package aisafe.flights.application;

import aisafe.flights.application.dtos.AircraftDTO;
import aisafe.flights.application.dtos.RouteDTO;
import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.flights.domain.AircraftUnavailableException;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.flights.infrastructure.catalog.CatalogAircraftServiceClient;
import aisafe.flights.infrastructure.catalog.CatalogRouteServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {"aisafe.messaging.enabled=false", "aisafe.read-model.rebuild-on-startup=false"})
@ActiveProfiles("jpa")
class ScheduleConcurrencyIntegrationTest {
    private static final OffsetDateTime T0 = OffsetDateTime.of(2028, 1, 10, 8, 0, 0, 0, ZoneOffset.UTC);

    @Autowired private ScheduleFlightUseCase scheduleFlight;
    @Autowired private ScheduledFlightRepository flights;

    @MockitoBean private CatalogAircraftServiceClient aircraftService;
    @MockitoBean private CatalogRouteServiceClient routeService;

    @Test
    void onlyOneOfTwoSimultaneousOverlappingFlightsIsAccepted() throws Exception {
        when(routeService.findRoute(anyString(), anyString()))
                .thenReturn(Optional.of(new RouteDTO("LIS", "OPO", 100.0, 50, "ACTIVE")));

        when(aircraftService.findByRegistration("CS-RACE")).thenAnswer(invocation -> {
            Thread.sleep(300);
            return Optional.of(new AircraftDTO("CS-RACE", "A320", "AVAILABLE", 180, 6000.0));
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            OffsetDateTime departure = T0.plusMinutes(i * 30L);
            results.add(pool.submit(() -> {
                start.await();
                try {
                    scheduleFlight.execute(new ScheduleFlightRequest("CS-RACE", "LIS", "OPO", departure, departure.plusHours(2)),
                            FlightStatus.SCHEDULED, QueryScope.LOCAL);
                    return "ACCEPTED";
                } catch (AircraftUnavailableException e) {
                    return "REJECTED";
                }
            }));
        }
        start.countDown();

        List<String> outcomes = new ArrayList<>();
        for (Future<String> result : results) {
            outcomes.add(result.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertTrue(outcomes.contains("ACCEPTED") && outcomes.contains("REJECTED"), "outcomes: " + outcomes);
        assertEquals(1, flights.findByAircraftRegistration("CS-RACE").size());
    }

    @Test
    void fiftySimultaneousPairsNeverProduceTwoFlightsAtOnce() throws Exception {
        when(routeService.findRoute(anyString(), anyString()))
                .thenReturn(Optional.of(new RouteDTO("LIS", "OPO", 100.0, 50, "ACTIVE")));
        when(aircraftService.findByRegistration("CS-PAIR"))
                .thenReturn(Optional.of(new AircraftDTO("CS-PAIR", "A320", "AVAILABLE", 180, 6000.0)));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        int doubleAccepted = 0;
        for (int day = 0; day < 50; day++) {
            OffsetDateTime departure = T0.plusDays(100 + day);
            CyclicBarrier together = new CyclicBarrier(2);
            Callable<Boolean> attempt = () -> {
                together.await();
                try {
                    scheduleFlight.execute(new ScheduleFlightRequest("CS-PAIR", "LIS", "OPO", departure, departure.plusHours(1)),
                            FlightStatus.SCHEDULED, QueryScope.LOCAL);
                    return true;
                } catch (AircraftUnavailableException e) {
                    return false;
                }
            };
            Future<Boolean> first = pool.submit(attempt);
            Future<Boolean> second = pool.submit(attempt);
            boolean a = first.get(30, TimeUnit.SECONDS);
            boolean b = second.get(30, TimeUnit.SECONDS);
            assertTrue(a || b, "one of each pair is accepted");
            if (a && b) {
                doubleAccepted++;
            }
        }
        pool.shutdown();

        assertEquals(0, doubleAccepted);
        assertEquals(50, flights.findByAircraftRegistration("CS-PAIR").size());
    }
}
