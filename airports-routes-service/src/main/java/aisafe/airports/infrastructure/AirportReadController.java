package aisafe.airports.infrastructure;

import aisafe.airports.application.AirportReadQueryService;
import aisafe.airports.infrastructure.persistence.jpa.AirportReadView;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/airports")
public class AirportReadController {

    private final AirportReadQueryService queryService;

    public AirportReadController(AirportReadQueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(summary = "Search the airport read model")
    @GetMapping
    public ResponseEntity<Page<AirportReadView>> search(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(
                queryService.search(name, city, country, pageable)
        );
    }
}
