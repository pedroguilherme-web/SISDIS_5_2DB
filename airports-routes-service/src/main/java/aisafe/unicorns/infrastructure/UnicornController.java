package aisafe.unicorns.infrastructure;

import aisafe.unicorns.application.AirplaneMoodQueryService;
import aisafe.unicorns.application.AirplaneMoodResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CQRS Query-Side controller for the airplane mood read model.
 *
 * <p>This endpoint serves data exclusively from the {@code airplane_mood_read_model}
 * projection table — the CQRS read model populated asynchronously by the
 * {@code AircraftMoodProjectionListener} via the RabbitMQ topic exchange
 * {@code aisafe.aircraft.events} (routing key {@code aircraft.mood.changed}).
 *
 * <p>It never touches the write-model aggregate in the Aircraft &amp; Maintenance
 * service. If AMQP is disabled or no events have arrived, the endpoint returns
 * the current (possibly empty) projection without local demo data.
 *
 * <p>CQRS mapping:
 * <ul>
 *   <li><b>Write side:</b> Aircraft &amp; Maintenance Service publishes
 *       {@code AircraftMoodChangedEvent} messages.</li>
 *   <li><b>Event bus:</b> RabbitMQ topic exchange {@code aisafe.aircraft.events}.</li>
 *   <li><b>Projection:</b> {@code AircraftMoodProjectionListener} updates
 *       {@code airplane_mood_read_model}.</li>
 *   <li><b>Read side (this endpoint):</b> queries only the projection table.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v2/unicorns")
@Tag(name = "Unicorns — CQRS Read Model", description = "Query-side projection of airplane mood statuses (airplane_mood_read_model)")
public class UnicornController {

    private final AirplaneMoodQueryService queryService;

    public UnicornController(AirplaneMoodQueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(
            summary = "List airplane mood statuses (CQRS read model)",
            description = "Returns all entries from the airplane_mood_read_model projection table. "
                    + "Data is populated asynchronously from AircraftMoodChangedEvent messages consumed from RabbitMQ. "
                    + "This is a pure query-side read: no write-model aggregate is touched. "
                    + "When AMQP is disabled or no event has arrived, the projection may be empty."
    )
    @GetMapping
    public ResponseEntity<List<AirplaneMoodResponse>> getAirplaneMoods() {
        return ResponseEntity.ok(queryService.findAll());
    }
}
