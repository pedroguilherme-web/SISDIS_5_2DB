package aisafe.routes.infrastructure;

import aisafe.routes.application.RouteReadQueryService;
import aisafe.routes.infrastructure.persistence.jpa.RouteReadView;
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
@RequestMapping("/api/v2/routes")
public class RouteReadController {

    private final RouteReadQueryService queryService;

    public RouteReadController(RouteReadQueryService queryService) {
        this.queryService = queryService;
    }

    @Operation(summary = "Search the route CQRS read model")
    @GetMapping
    public ResponseEntity<Page<RouteReadView>> search(
            @RequestParam(required = false) String origin,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(queryService.search(origin, destination, status, pageable));
    }
}
