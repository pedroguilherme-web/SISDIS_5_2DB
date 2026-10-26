
package aisafe.p2p;

import aisafe.airports.application.SearchAirportUseCase;
import aisafe.airports.application.dtos.AirportResponse;
import aisafe.airports.domain.AirportRepository;
import aisafe.shared.domain.PaginatedResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AirportLocalSearchProviderTest {

    @Test
    void shouldSerializeLocalAirportsAsJson() throws Exception {

        AirportRepository repository = mock(AirportRepository.class);

        // Usamos o caso de uso real com um repositório simulado
        SearchAirportUseCase searchAirport =
                new SearchAirportUseCase(repository);

        AirportLocalSearchProvider provider =
                new AirportLocalSearchProvider(searchAirport);

        // Como o repositório devolve entidades Airport,
        // testamos primeiro o comportamento sem resultados.
        when(repository.searchAirports(
                isNull(),
                isNull(),
                isNull(),
                eq(0),
                eq(100)
        )).thenReturn(new PaginatedResult<>(List.of(), 0));

        String result = provider.searchLocal("/api/airports/search");

        var json = new ObjectMapper().readTree(result);

        assertTrue(json.isArray());
        assertEquals(0, json.size());

        verify(repository).searchAirports(
                null, null, null, 0, 100
        );
    }

    @Test
    void shouldIgnoreUnsupportedPaths() {

        AirportRepository repository = mock(AirportRepository.class);

        SearchAirportUseCase searchAirport =
                new SearchAirportUseCase(repository);

        AirportLocalSearchProvider provider =
                new AirportLocalSearchProvider(searchAirport);

        String result = provider.searchLocal("/api/unknown");

        assertEquals("[]", result);

        verifyNoInteractions(repository);
    }


    @Test
    void shouldSerializeAirportWithData() throws Exception {

        AirportRepository repository = mock(AirportRepository.class);

        SearchAirportUseCase searchAirport =
                new SearchAirportUseCase(repository);

        AirportLocalSearchProvider provider =
                new AirportLocalSearchProvider(searchAirport);

        // Criar uma entidade Airport simulada com dados preenchidos
        aisafe.airports.domain.Airport airport =
                mock(aisafe.airports.domain.Airport.class);

        aisafe.airports.domain.IataCode iataCode =
                mock(aisafe.airports.domain.IataCode.class);

        aisafe.airports.domain.Coordinates coordinates =
                mock(aisafe.airports.domain.Coordinates.class);

        when(airport.getIataCode()).thenReturn(iataCode);
        when(iataCode.getCode()).thenReturn("OPO");

        when(airport.getName()).thenReturn("Francisco Sá Carneiro");
        when(airport.getCity()).thenReturn("Porto");
        when(airport.getCountry()).thenReturn("Portugal");
        when(airport.getRegion()).thenReturn("Norte");
        when(airport.getTimezone()).thenReturn("Europe/Lisbon");

        when(airport.getCoordinates()).thenReturn(coordinates);
        when(coordinates.getLatitude()).thenReturn(41.2481);
        when(coordinates.getLongitude()).thenReturn(-8.6814);

        when(airport.getStatus())
                .thenReturn(aisafe.airports.domain.AirportStatus.OPERATIONAL);

        when(airport.getPhotos()).thenReturn(List.of());
        when(airport.getRunways()).thenReturn(List.of());
        when(airport.getContacts()).thenReturn(List.of());
        when(airport.getServices()).thenReturn(List.of());
        when(airport.getTerminals()).thenReturn(List.of());
        when(airport.getGates()).thenReturn(List.of());

        when(repository.searchAirports(
                isNull(),
                isNull(),
                isNull(),
                eq(0),
                eq(100)
        )).thenReturn(new PaginatedResult<>(
                List.of(airport),
                1
        ));

        String result = provider.searchLocal("/api/airports/search");

        var json = new ObjectMapper().readTree(result);

        assertTrue(json.isArray());
        assertEquals(1, json.size());

        var airportJson = json.get(0);

        assertEquals("OPO", airportJson.get("iataCode").asText());
        assertEquals("Porto", airportJson.get("city").asText());
        assertEquals("Portugal", airportJson.get("country").asText());
        assertEquals(
                41.2481,
                airportJson.get("coordinates").get("latitude").asDouble(),
                0.0001
        );

        verify(repository).searchAirports(
                null, null, null, 0, 100
        );
    }


}
