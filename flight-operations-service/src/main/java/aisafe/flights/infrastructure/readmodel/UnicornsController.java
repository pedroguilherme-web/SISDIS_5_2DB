package aisafe.flights.infrastructure.readmodel;

import aisafe.flights.infrastructure.readmodel.ReadModelResponses.AirplaneMoodView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v2/unicorns")
@Tag(name = "Unicorns - CQRS read model", description = "Airplane moods projected from flight and aircraft events")
public class UnicornsController {
    private final FlightQueryService queries;

    public UnicornsController(FlightQueryService queries) {
        this.queries = queries;
    }

    @Operation(summary = "Mood of every airplane known to this instance")
    @GetMapping
    public List<AirplaneMoodView> moods() {
        return queries.moods();
    }

    @Operation(summary = "Mood of one airplane")
    @GetMapping("/{registration}")
    public AirplaneMoodView mood(@PathVariable String registration) {
        return queries.mood(registration);
    }
}
