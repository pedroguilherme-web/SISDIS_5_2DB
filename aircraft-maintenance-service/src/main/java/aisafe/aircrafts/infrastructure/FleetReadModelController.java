package aisafe.aircrafts.infrastructure;

import aisafe.aircrafts.cqrs.projections.AircraftFleetSummaryResponse;
import aisafe.aircrafts.cqrs.projections.AircraftReadModel;
import aisafe.aircrafts.cqrs.projections.AircraftReadModelRepository;
import aisafe.aircrafts.domain.Aircraft;
import aisafe.aircrafts.domain.AircraftNotFoundException;
import aisafe.aircrafts.domain.AircraftRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * CQRS Query / Read Model REST Controller for LETI-SIDIS Assignment 2:
 * GET /api/v2/fleet - Returns aggregated fleet operational and maintenance status in JSON format.
 * Demonstrates a dedicated CQRS Read Model projection querying 'aircraft_read_model' without relational joins.
 */
@RestController
@RequestMapping("/api/v2/fleet")
@Tag(name = "Fleet Read Model - CQRS Projections",
        description = "Query endpoints returning pre-calculated fleet operational readiness projected via CQRS")
public class FleetReadModelController {

    private final AircraftReadModelRepository readModelRepository;
    private final AircraftRepository aircraftRepository;

    @Autowired
    public FleetReadModelController(AircraftReadModelRepository readModelRepository, AircraftRepository aircraftRepository) {
        this.readModelRepository = readModelRepository;
        this.aircraftRepository = aircraftRepository;
    }

    @Operation(summary = "Get fleet operational readiness summary",
            description = "Returns a list of all aircraft with their projected operational readiness status (READY_FOR_FLIGHT, UNDER_MAINTENANCE, MAINTENANCE_DUE, GROUNDED) and accumulated flight hours.")
    @ApiResponse(responseCode = "200", description = "Fleet operational projections returned successfully")
    @GetMapping
    public ResponseEntity<List<AircraftFleetSummaryResponse>> getFleetSummary() {
        ensureReadModelPopulated();

        List<AircraftFleetSummaryResponse> response = readModelRepository.findAll()
                .stream()
                .map(AircraftFleetSummaryResponse::fromReadModel)
                .toList();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get operational readiness for a specific aircraft",
            description = "Returns the operational readiness projection for a specific aircraft by registration code.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Aircraft projection returned successfully"),
            @ApiResponse(responseCode = "404", description = "Aircraft not found")
    })
    @GetMapping("/{registration}")
    public ResponseEntity<AircraftFleetSummaryResponse> getAircraftSummary(
            @Parameter(description = "Aircraft registration number (e.g. CS-TKA)")
            @PathVariable String registration) {

        ensureReadModelPopulated();

        return readModelRepository.findByRegistrationNumber(registration)
                .map(AircraftFleetSummaryResponse::fromReadModel)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new AircraftNotFoundException("Aircraft with registration " + registration + " not found."));
    }

    /**
     * Self-healing read model synchronization:
     * If read model is empty upon startup with in-memory H2 database,
     * seeds initial projection entries from the write-side repository.
     */
    private void ensureReadModelPopulated() {
        if (readModelRepository.count() == 0) {
            List<Aircraft> aircrafts = aircraftRepository.findAll();
            for (Aircraft a : aircrafts) {
                AircraftReadModel rm = new AircraftReadModel(
                        a.getRegistrationNumber().getNumber(),
                        a.getModel() != null ? a.getModel().getModelName() : "Unknown",
                        a.getStatus() != null ? a.getStatus().name() : "AVAILABLE",
                        a.getModel() != null ? a.getModel().getMaximumSeatingCapacity() : 180,
                        a.getModel() != null ? a.getModel().getMaxRange() : 6000.0
                );
                readModelRepository.save(rm);
            }
        }
    }
}
