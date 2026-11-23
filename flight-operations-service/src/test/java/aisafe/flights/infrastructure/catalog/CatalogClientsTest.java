package aisafe.flights.infrastructure.catalog;

import aisafe.flights.application.ExternalServiceUnavailableException;
import aisafe.flights.application.dtos.AircraftDTO;
import aisafe.flights.application.dtos.RouteDTO;
import aisafe.flights.infrastructure.http.HttpAircraftServiceClient;
import aisafe.flights.infrastructure.http.HttpRouteServiceClient;
import aisafe.flights.infrastructure.persistence.jpa.catalog.AircraftCatalogEntry;
import aisafe.flights.infrastructure.persistence.jpa.catalog.AircraftCatalogRepository;
import aisafe.flights.infrastructure.persistence.jpa.catalog.RouteCatalogEntry;
import aisafe.flights.infrastructure.persistence.jpa.catalog.RouteCatalogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CatalogClientsTest {
    private static final Instant NOW = Instant.parse("2026-11-22T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration MAX_AGE = Duration.ofMinutes(10);

    @Mock private AircraftCatalogRepository aircraftCatalog;
    @Mock private RouteCatalogRepository routeCatalog;
    @Mock private CatalogUpdater updater;
    @Mock private HttpAircraftServiceClient aircraftHttp;
    @Mock private HttpRouteServiceClient routeHttp;

    private static AircraftCatalogEntry aircraftEntry(String status, Instant refreshedAt) {
        AircraftCatalogEntry entry = new AircraftCatalogEntry("CS-TRP");
        entry.setModel("A320");
        entry.setStatus(status);
        entry.setSeatCapacity(180);
        entry.setRangeKm(6100.0);
        entry.setRefreshedAt(refreshedAt);
        return entry;
    }

    @Test
    void shouldAnswerFromAFreshCatalogEntryWithoutCallingTheAircraftService() {
        when(aircraftCatalog.findById("CS-TRP")).thenReturn(Optional.of(aircraftEntry("UNDER_MAINTENANCE", NOW.minusSeconds(60))));
        var client = new CatalogAircraftServiceClient(aircraftCatalog, updater, aircraftHttp, MAX_AGE, CLOCK);

        AircraftDTO aircraft = client.findByRegistration("cs-trp").orElseThrow();

        assertEquals("UNDER_MAINTENANCE", aircraft.status());
        verifyNoInteractions(aircraftHttp);
    }

    @Test
    void shouldRefreshAnOldEntryOverHttpAndCacheTheAnswer() {
        when(aircraftCatalog.findById("CS-TRP")).thenReturn(Optional.of(aircraftEntry("AVAILABLE", NOW.minus(Duration.ofHours(1)))));
        AircraftDTO remote = new AircraftDTO("CS-TRP", "A320", "INACTIVE", 180, 6100.0);
        when(aircraftHttp.findByRegistration("CS-TRP")).thenReturn(Optional.of(remote));
        var client = new CatalogAircraftServiceClient(aircraftCatalog, updater, aircraftHttp, MAX_AGE, CLOCK);

        assertEquals("INACTIVE", client.findByRegistration("CS-TRP").orElseThrow().status());
        verify(updater).aircraftFetched(remote, "CS-TRP");
    }

    @Test
    void shouldNotFallBackToAnOldEntryWhenTheAircraftServiceIsDown() {
        when(aircraftCatalog.findById("CS-TRP")).thenReturn(Optional.of(aircraftEntry("AVAILABLE", NOW.minus(Duration.ofHours(1)))));
        when(aircraftHttp.findByRegistration("CS-TRP")).thenThrow(new ExternalServiceUnavailableException("Aircraft & Maintenance service"));
        var client = new CatalogAircraftServiceClient(aircraftCatalog, updater, aircraftHttp, MAX_AGE, CLOCK);

        assertThrows(ExternalServiceUnavailableException.class, () -> client.findByRegistration("CS-TRP"));
    }

    @Test
    void shouldAlwaysAskTheServiceWhenNoEventsKeepTheCatalogFresh() {
        when(aircraftCatalog.findById("CS-TRP")).thenReturn(Optional.of(aircraftEntry("AVAILABLE", NOW)));
        when(aircraftHttp.findByRegistration("CS-TRP")).thenReturn(Optional.empty());
        var client = new CatalogAircraftServiceClient(aircraftCatalog, updater, aircraftHttp, Duration.ZERO, CLOCK);

        assertTrue(client.findByRegistration("CS-TRP").isEmpty());
        verify(aircraftHttp).findByRegistration("CS-TRP");
    }

    @Test
    void shouldAnswerNotFoundForARouteDeletedByARecentEvent() {
        RouteCatalogEntry deleted = new RouteCatalogEntry("LIS", "OPO");
        deleted.setDeleted(true);
        deleted.setRefreshedAt(NOW.minusSeconds(5));
        when(routeCatalog.findById("LIS-OPO")).thenReturn(Optional.of(deleted));
        var client = new CatalogRouteServiceClient(routeCatalog, updater, routeHttp, MAX_AGE, CLOCK);

        assertTrue(client.findRoute("lis", "opo").isEmpty());
        verifyNoInteractions(routeHttp);
    }

    @Test
    void shouldFetchAnUnknownRouteOverHttpAndCacheIt() {
        when(routeCatalog.findById("LIS-OPO")).thenReturn(Optional.empty());
        RouteDTO remote = new RouteDTO("LIS", "OPO", 300.0, 100, "ACTIVE");
        when(routeHttp.findRoute("LIS", "OPO")).thenReturn(Optional.of(remote));
        var client = new CatalogRouteServiceClient(routeCatalog, updater, routeHttp, MAX_AGE, CLOCK);

        assertEquals(remote, client.findRoute("LIS", "OPO").orElseThrow());
        verify(updater).routeFetched(remote, "LIS", "OPO");
    }
}
