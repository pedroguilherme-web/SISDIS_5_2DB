package aisafe.airports.infrastructure.messaging;

import aisafe.airports.application.AirportReadQueryService;
import aisafe.airports.infrastructure.persistence.jpa.AirportReadViewRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class AirportReadModelProjectionIntegrationTest {

    @Autowired
    private AirportReadViewRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void v2QueryReadsOnlyTheAmqpProjectionAndHidesDeletedAirports() throws Exception {
        AirportReadModelProjectionListener listener =
                new AirportReadModelProjectionListener(objectMapper, repository);
        AirportReadQueryService queryService = new AirportReadQueryService(repository);
        var pageable = PageRequest.of(0, 20);

        assertEquals(0, queryService.search(null, null, null, pageable).getTotalElements());

        listener.project(payload("AirportRegistered", "2026-11-18T10:00:00Z"));
        var projected = queryService.search(null, null, null, pageable);
        assertEquals(1, projected.getTotalElements());
        assertEquals("P2T", projected.getContent().getFirst().getIataCode());

        listener.project(payload("AirportDeleted", "2026-11-18T11:00:00Z"));

        assertEquals(0, queryService.search(null, null, null, pageable).getTotalElements());
        assertTrue(repository.findById("P2T").orElseThrow().isDeleted());
    }

    private byte[] payload(String eventType, String occurredAt) throws Exception {
        return objectMapper.writeValueAsBytes(new AirportChangedEvent(
                "event-" + eventType,
                "correlation-" + eventType,
                eventType,
                "P2T",
                "P2 Test Airport",
                "Porto",
                "Portugal",
                "Europe",
                "Europe/Lisbon",
                "OPERATIONAL",
                Instant.parse(occurredAt)));
    }
}
