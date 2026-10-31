
package aisafe.p2p;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;

@RestController
@RequestMapping("/api/p2p")
public class P2PSearchController {

    private final DistributedSearchService searchService;

    public P2PSearchController(
            DistributedSearchService searchService) {

        this.searchService = Objects.requireNonNull(
                searchService
        );
    }

    @PostMapping(
            value = "/search",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public String search(
            @RequestBody DistributedSearchRequest request) {

        return searchService.processSearch(request);
    }
}
