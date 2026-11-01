package aisafe.flights.infrastructure;

import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.p2p.LocalSearchProvider;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FlightLocalSearchProvider implements LocalSearchProvider {

    private final ScheduledFlightRepository scheduledFlightRepository;
    private final ObjectMapper objectMapper;

    @Autowired
    public FlightLocalSearchProvider(ScheduledFlightRepository scheduledFlightRepository) {
        this.scheduledFlightRepository = scheduledFlightRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    // Construtor auxiliar para testes unitários com mock do ObjectMapper
    FlightLocalSearchProvider(ScheduledFlightRepository scheduledFlightRepository, ObjectMapper objectMapper) {
        this.scheduledFlightRepository = scheduledFlightRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public String searchLocal(String path) {
        if (path == null || !path.startsWith("/api/flights")) {
            return "[]";
        }

        var flights = scheduledFlightRepository.findAll().stream()
                .map(FlightResponse::from)
                .toList();

        try {
            return objectMapper.writeValueAsString(flights);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize local flight results", e);
        }
    }
}