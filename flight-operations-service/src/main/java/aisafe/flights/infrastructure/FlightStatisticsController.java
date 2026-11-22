package aisafe.flights.infrastructure;

import aisafe.flights.application.FlightStatisticsUseCase;
import aisafe.flights.application.dtos.FlightTimelineResponse;
import aisafe.flights.application.dtos.TopModelResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;

@RestController
@RequestMapping("/api/flights")
@Tag(name = "Flight Statistics", description = "Figures used by the Aircraft & Maintenance and Airports & Routes services")
public class FlightStatisticsController {
    private final FlightStatisticsUseCase statistics;

    public FlightStatisticsController(FlightStatisticsUseCase statistics) {
        this.statistics = statistics;
    }

    @Operation(summary = "Total completed flight hours of an aircraft")
    @GetMapping("/aircraft/{registration}/hours")
    public Double totalHours(@PathVariable String registration,
                             @Parameter(hidden = true) @RequestHeader(value = PeerForwarding.HEADER, required = false) String forwarded) {
        return statistics.totalHours(registration, PeerForwarding.scopeOf(forwarded));
    }

    @Operation(summary = "Completed flight hours of an aircraft since a date",
            description = "'since' accepts ISO-8601 with or without offset; without offset it is taken as UTC.")
    @GetMapping("/aircraft/{registration}/hours-since")
    public Double hoursSince(@PathVariable String registration,
                             @RequestParam String since,
                             @Parameter(hidden = true) @RequestHeader(value = PeerForwarding.HEADER, required = false) String forwarded) {
        return statistics.hoursSince(registration, parseDateTime(since, "since"), PeerForwarding.scopeOf(forwarded));
    }

    @Operation(summary = "Whether an aircraft has any flight")
    @GetMapping("/aircraft/{registration}/exists")
    public Boolean exists(@PathVariable String registration,
                          @Parameter(hidden = true) @RequestHeader(value = PeerForwarding.HEADER, required = false) String forwarded) {
        return statistics.hasFlights(registration, PeerForwarding.scopeOf(forwarded));
    }

    @Operation(summary = "Flights of an aircraft in chronological order")
    @GetMapping("/aircraft/{registration}/timeline")
    public List<FlightTimelineResponse> timeline(@PathVariable String registration,
                                                 @Parameter(hidden = true) @RequestHeader(value = PeerForwarding.HEADER, required = false) String forwarded) {
        return statistics.timeline(registration, PeerForwarding.scopeOf(forwarded));
    }

    @Operation(summary = "Whether this replica has a flight of the aircraft overlapping an interval",
            description = "Used by peer replicas while scheduling. Always answers with local data only.")
    @GetMapping("/aircraft/{registration}/conflicts")
    public Boolean conflicts(@PathVariable String registration,
                             @RequestParam String departure,
                             @RequestParam String arrival) {
        return statistics.hasOverlappingFlight(registration, parseDateTime(departure, "departure"), parseDateTime(arrival, "arrival"));
    }

    @Operation(summary = "Aircraft models ranked by completed flight hours")
    @GetMapping("/top-models/hours")
    public List<TopModelResponse> topModelsByHours(@RequestParam(defaultValue = "5") int limit,
                                                   @Parameter(hidden = true) @RequestHeader(value = PeerForwarding.HEADER, required = false) String forwarded) {
        return statistics.topModelsByHours(limit, PeerForwarding.scopeOf(forwarded));
    }

    @Operation(summary = "Aircraft models ranked by number of completed flights")
    @GetMapping("/top-models/assignments")
    public List<TopModelResponse> topModelsByAssignments(@RequestParam(defaultValue = "5") int limit,
                                                         @Parameter(hidden = true) @RequestHeader(value = PeerForwarding.HEADER, required = false) String forwarded) {
        return statistics.topModelsByAssignments(limit, PeerForwarding.scopeOf(forwarded));
    }

    @Operation(summary = "Number of flights on a route")
    @GetMapping("/count")
    public Long countByRoute(@RequestParam String origin,
                             @RequestParam String destination,
                             @Parameter(hidden = true) @RequestHeader(value = PeerForwarding.HEADER, required = false) String forwarded) {
        return statistics.countByRoute(origin, destination, PeerForwarding.scopeOf(forwarded));
    }

    static OffsetDateTime parseDateTime(String value, String parameter) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException withoutOffset) {
            try {
                return LocalDateTime.parse(value).atOffset(ZoneOffset.UTC);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid '" + parameter + "': " + value + ". Expected ISO-8601, e.g. 2026-01-01T00:00:00Z.");
            }
        }
    }
}
