package aisafe.flights.infrastructure.readmodel;

import aisafe.flights.infrastructure.readmodel.ReadModelResponses.AircraftActivity;
import aisafe.flights.infrastructure.readmodel.ReadModelResponses.FlightView;
import aisafe.flights.infrastructure.readmodel.ReadModelResponses.PageResponse;
import aisafe.flights.infrastructure.readmodel.ReadModelResponses.RebuildResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/flights")
@Tag(name = "Flights v2 - CQRS queries", description = "Read model fed by AMQP events (eventually consistent)")
public class FlightQueryController {
    private final FlightQueryService queries;
    private final ReadModelRebuilder rebuilder;

    public FlightQueryController(FlightQueryService queries, ReadModelRebuilder rebuilder) {
        this.queries = queries;
        this.rebuilder = rebuilder;
    }

    @Operation(summary = "Search flights in the read model", description = "Optional filters: aircraftId, origin, destination, status.")
    @GetMapping
    public PageResponse<FlightView> search(@RequestParam(required = false) String aircraftId,
                                           @RequestParam(required = false) String origin,
                                           @RequestParam(required = false) String destination,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        return queries.search(aircraftId, origin, destination, status, page, size);
    }

    @Operation(summary = "One flight from the read model", description = "404 until the flight's event has been projected on this instance.")
    @GetMapping("/{flightId}")
    public FlightView flight(@PathVariable String flightId) {
        return queries.flight(flightId);
    }

    @Operation(summary = "Pre-aggregated flight activity of an aircraft")
    @GetMapping("/aircraft/{registration}/activity")
    public AircraftActivity activity(@PathVariable String registration) {
        return queries.activity(registration);
    }

    @Operation(summary = "Rebuild this instance's read model from the write models of the cluster")
    @PostMapping("/read-model/rebuild")
    public RebuildResult rebuild() {
        return rebuilder.rebuild();
    }
}
