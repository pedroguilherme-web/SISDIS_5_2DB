package aisafe.flights.infrastructure.readmodel;

import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.events.FlightEvent;
import aisafe.flights.infrastructure.http.JsonBodies;
import aisafe.flights.infrastructure.readmodel.ReadModelResponses.RebuildResult;
import aisafe.p2p.DistributedSearchRequest;
import aisafe.p2p.DistributedSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class ReadModelRebuilder {
    private final DistributedSearchService distributedSearch;
    private final FlightProjector projector;
    private final boolean rebuildOnStartup;

    public ReadModelRebuilder(DistributedSearchService distributedSearch, FlightProjector projector,
                              @Value("${aisafe.read-model.rebuild-on-startup:true}") boolean rebuildOnStartup) {
        this.distributedSearch = distributedSearch;
        this.projector = projector;
        this.rebuildOnStartup = rebuildOnStartup;
    }

    public RebuildResult rebuild() {
        String json = distributedSearch.processSearch(DistributedSearchRequest.create("/api/flights", 1));
        List<FlightResponse> flights = JsonBodies.readList(json, FlightResponse.class);
        int applied = 0;
        for (FlightResponse flight : flights) {
            if (projector.apply(FlightEvent.snapshot(flight))) {
                applied++;
            }
        }
        log.info("Read model rebuild: {} flights received from the cluster, {} applied", flights.size(), applied);
        return new RebuildResult(flights.size(), applied);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void rebuildOnStartup() {
        if (!rebuildOnStartup) {
            return;
        }
        try {
            rebuild();
        } catch (RuntimeException e) {
            log.warn("Read model rebuild on startup failed; it will be filled by events: {}", e.getMessage());
        }
    }
}
