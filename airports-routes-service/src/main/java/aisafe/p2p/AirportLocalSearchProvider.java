
package aisafe.p2p;

import aisafe.airports.application.SearchAirportUseCase;
import aisafe.airports.application.dtos.SearchAirportRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class AirportLocalSearchProvider implements LocalSearchProvider {

    private final SearchAirportUseCase searchAirport;
    private final ObjectMapper objectMapper;

    public AirportLocalSearchProvider(
            SearchAirportUseCase searchAirport) {

        this.searchAirport = searchAirport;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String searchLocal(String path) {

        if (!"/api/airports/search".equals(path)) {
            return "[]";
        }

        SearchAirportRequest request =
                new SearchAirportRequest(
                        null,
                        null,
                        null,
                        0,
                        100
                );

        var result = searchAirport.execute(request);

        try {
            return objectMapper.writeValueAsString(result.data());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize local airport results",
                    exception
            );
        }
    }
}
