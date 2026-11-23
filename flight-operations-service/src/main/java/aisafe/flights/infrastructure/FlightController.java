package aisafe.flights.infrastructure;

import aisafe.flights.application.ChangeFlightStatusUseCase;
import aisafe.flights.application.ImportFlightsUseCase;
import aisafe.flights.application.QueryScope;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.infrastructure.http.HttpFlightPeerClient;
import aisafe.flights.application.ScheduleFlightUseCase;
import aisafe.flights.application.ViewScheduledFlightsByAircraftUseCase;
import aisafe.flights.application.dtos.ChangeFlightStatusRequest;
import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.shared.application.dtos.BulkImportResult;
import aisafe.shared.infrastructure.BulkImportResponseBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
@RequestMapping("/api/flights")
@Tag(name = "Flights", description = "Scheduled Flight Management - WP#3B")
public class FlightController {
    private final ScheduleFlightUseCase scheduleFlight;
    private final ViewScheduledFlightsByAircraftUseCase viewScheduledFlightsByAircraft;
    private final ChangeFlightStatusUseCase changeFlightStatus;
    private final ImportFlightsUseCase importFlightsUseCase;

    public FlightController(ScheduleFlightUseCase scheduleFlight,
                            ViewScheduledFlightsByAircraftUseCase viewScheduledFlightsByAircraft,
                            ChangeFlightStatusUseCase changeFlightStatus,
                            ImportFlightsUseCase importFlightsUseCase) {
        this.scheduleFlight = scheduleFlight;
        this.viewScheduledFlightsByAircraft = viewScheduledFlightsByAircraft;
        this.changeFlightStatus = changeFlightStatus;
        this.importFlightsUseCase = importFlightsUseCase;
    }

    private EntityModel<FlightResponse> toModel(FlightResponse flight) {
        return EntityModel.of(flight,
                linkTo(methodOn(FlightController.class).getScheduledFlightsByAircraft(flight.aircraftId(), null)).withRel("aircraft-flights"),
                linkTo(methodOn(FlightController.class).changeStatus(flight.id(), null, null)).withRel("change-status"));
    }

    @Operation(summary = "Assign aircraft to route", description = "Creates a scheduled flight for an aircraft and route, "
            + "after checking the route and aircraft with their services and checking schedule conflicts on every replica. (US212)")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Scheduled flight created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid schedule or business rule violation"),
            @ApiResponse(responseCode = "404", description = "Aircraft or route not found"),
            @ApiResponse(responseCode = "409", description = "Aircraft unavailable in the requested timeframe"),
            @ApiResponse(responseCode = "503", description = "Aircraft or Routes service, or the replica that owns the aircraft, unavailable")
    })
    @PostMapping
    public ResponseEntity<EntityModel<FlightResponse>> scheduleFlight(
            @Valid @RequestBody ScheduleFlightRequest request,
            @Parameter(hidden = true) @RequestHeader(value = PeerForwarding.HEADER, required = false) String forwarded,
            @Parameter(hidden = true) @RequestHeader(value = HttpFlightPeerClient.INITIAL_STATUS_HEADER, required = false) String initialStatus) {
        QueryScope scope = PeerForwarding.scopeOf(forwarded);

        FlightStatus status = scope == QueryScope.LOCAL && initialStatus != null
                ? FlightStatus.valueOf(initialStatus.trim().toUpperCase())
                : FlightStatus.SCHEDULED;
        FlightResponse response = scheduleFlight.execute(request, status, scope);
        return ResponseEntity.status(HttpStatus.CREATED).body(toModel(response));
    }

    @Operation(summary = "View scheduled flights by aircraft", description = "Lists the flights of an aircraft stored on every replica. (US213)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Scheduled flights retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Missing or invalid aircraft identifier"),
            @ApiResponse(responseCode = "404", description = "Aircraft not found")
    })
    @GetMapping
    public ResponseEntity<CollectionModel<EntityModel<FlightResponse>>> getScheduledFlightsByAircraft(
            @RequestParam String aircraftId,
            @Parameter(hidden = true) @RequestHeader(value = PeerForwarding.HEADER, required = false) String forwarded) {
        List<EntityModel<FlightResponse>> flights = viewScheduledFlightsByAircraft
                .execute(aircraftId, PeerForwarding.scopeOf(forwarded)).stream()
                .map(this::toModel)
                .toList();
        return ResponseEntity.ok(CollectionModel.of(flights,
                linkTo(methodOn(FlightController.class).getScheduledFlightsByAircraft(aircraftId, null)).withSelfRel()));
    }

    @Operation(summary = "Change flight status", description = "Moves a flight through its lifecycle: "
            + "SCHEDULED -> DELAYED/IN_FLIGHT/CANCELED, DELAYED -> IN_FLIGHT/CANCELED, IN_FLIGHT -> COMPLETED. "
            + "Works on any replica: if the flight is stored on another one, the change is forwarded to it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status changed"),
            @ApiResponse(responseCode = "404", description = "Flight not found on any replica"),
            @ApiResponse(responseCode = "409", description = "Transition not allowed from the current status"),
            @ApiResponse(responseCode = "503", description = "Flight not found locally and a peer replica is unreachable")
    })
    @PatchMapping("/{flightId}/status")
    public ResponseEntity<EntityModel<FlightResponse>> changeStatus(
            @PathVariable String flightId,
            @Valid @RequestBody ChangeFlightStatusRequest request,
            @Parameter(hidden = true) @RequestHeader(value = PeerForwarding.HEADER, required = false) String forwarded) {
        FlightResponse response = changeFlightStatus.execute(flightId, request.status(), PeerForwarding.scopeOf(forwarded));
        return ResponseEntity.ok(toModel(response));
    }

    @Operation(summary = "Import flights", description = "Bulk import flights from a CSV file. The optional 'status' column is honoured.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "All flights imported successfully"),
            @ApiResponse(responseCode = "207", description = "Partial success or all failed"),
            @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importFlights(@RequestParam("file") MultipartFile file) throws IOException {
        try (InputStream csv = file.getInputStream()) {
            BulkImportResult<String> result = importFlightsUseCase.execute(csv);
            return BulkImportResponseBuilder.buildResponse(result);
        }
    }
}
