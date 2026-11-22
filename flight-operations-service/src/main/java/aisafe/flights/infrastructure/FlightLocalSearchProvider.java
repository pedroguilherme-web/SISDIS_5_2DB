package aisafe.flights.infrastructure;

import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.p2p.LocalSearchProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.function.Predicate;

@Component
public class FlightLocalSearchProvider implements LocalSearchProvider {
    private final ScheduledFlightRepository scheduledFlightRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public FlightLocalSearchProvider(ScheduledFlightRepository scheduledFlightRepository) {
        this(scheduledFlightRepository, new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS));
    }

    FlightLocalSearchProvider(ScheduledFlightRepository scheduledFlightRepository, ObjectMapper objectMapper) {
        this.scheduledFlightRepository = scheduledFlightRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String searchLocal(String path) {
        if (path == null || !path.startsWith("/api/flights")) {
            return "[]";
        }

        MultiValueMap<String, String> query = UriComponentsBuilder.fromUriString(path).build().getQueryParams();
        String aircraftId = first(query, "aircraftId");

        List<ScheduledFlight> candidates = aircraftId != null
                ? scheduledFlightRepository.findByAircraftRegistration(aircraftId.toUpperCase())
                : scheduledFlightRepository.findAll();

        List<FlightResponse> flights = candidates.stream()
                .filter(matches(first(query, "origin"), ScheduledFlight::getOriginCode))
                .filter(matches(first(query, "destination"), ScheduledFlight::getDestinationCode))
                .filter(matches(first(query, "status"), flight -> flight.getStatus().name()))
                .map(FlightResponse::from)
                .toList();

        try {
            return objectMapper.writeValueAsString(flights);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize local flight results", e);
        }
    }

    private static Predicate<ScheduledFlight> matches(String expected, java.util.function.Function<ScheduledFlight, String> field) {
        return flight -> expected == null || expected.equalsIgnoreCase(field.apply(flight));
    }

    private static String first(MultiValueMap<String, String> query, String name) {
        String value = query.getFirst(name);
        return value == null || value.isBlank() ? null : value.trim();
    }
}
