package aisafe.aircrafts.infrastructure;

import aisafe.aircrafts.cqrs.projections.AircraftMoodResponse;
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
 * Mandatory REST Controller for LETI-SIDIS Assignment 2:
 * GET /api/v2/unicorns - Returns the mood status of airplanes in JSON format.
 * Demonstrates a dedicated CQRS Read Model projection querying 'aircraft_read_model'.
 */
@RestController
@RequestMapping("/api/v2/unicorns")
@Tag(name = "Unicorns - Airplane Mood CQRS Projections",
        description = "Query endpoints returning airplane sentiment and mood states projected via CQRS")
public class UnicornsController {

    private final AircraftReadModelRepository readModelRepository;
    private final AircraftRepository aircraftRepository;

    @Autowired
    public UnicornsController(AircraftReadModelRepository readModelRepository, AircraftRepository aircraftRepository) {
        this.readModelRepository = readModelRepository;
        this.aircraftRepository = aircraftRepository;
    }

    @Operation(summary = "Get mood status of all airplanes",
            description = "Returns a list of all airplanes with their projected mood (HAPPY, TIRED, UNDER_CARE, GRUMPY, PROUD) and sparkleLevel.")
    @ApiResponse(responseCode = "200", description = "List of airplane mood projections returned successfully")
    @GetMapping
    public ResponseEntity<List<AircraftMoodResponse>> getAllAirplanesMood() {
        ensureReadModelPopulated();

        List<AircraftMoodResponse> response = readModelRepository.findAll()
                .stream()
                .map(AircraftMoodResponse::fromReadModel)
                .toList();

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get mood status of a specific airplane",
            description = "Returns the mood status of a specific airplane by registration code.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Airplane mood returned successfully"),
            @ApiResponse(responseCode = "404", description = "Airplane not found")
    })
    @GetMapping("/{registration}")
    public ResponseEntity<AircraftMoodResponse> getAirplaneMoodByRegistration(
            @Parameter(description = "Airplane registration number (e.g. CS-TKA)")
            @PathVariable String registration) {

        ensureReadModelPopulated();

        return readModelRepository.findByRegistrationNumber(registration)
                .map(AircraftMoodResponse::fromReadModel)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new AircraftNotFoundException("Aircraft with registration " + registration + " not found."));
    }

    /**
     * Self-healing read model synchronization:
     * If read model is empty (e.g., initial start with in-memory H2 database),
     * projects from the write-side repository.
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
