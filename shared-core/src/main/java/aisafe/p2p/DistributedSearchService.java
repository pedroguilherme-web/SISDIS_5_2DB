
package aisafe.p2p;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DistributedSearchService {

    private final PeerRegistry registry;
    private final PeerClient client;
    private final SearchRequestTracker tracker;

    public DistributedSearchService(
            PeerRegistry registry,
            PeerClient client) {

        this.registry = Objects.requireNonNull(registry);
        this.client = Objects.requireNonNull(client);
        this.tracker = new SearchRequestTracker();
    }

    public List<String> searchAllPeers(String path) {

        List<String> results = new ArrayList<>();

        for (PeerNode peer : registry.getAllPeers()) {
            try {
                String response = client.searchPeer(peer, path);

                if (response != null) {
                    results.add(response);
                }

            } catch (Exception exception) {
                System.err.println(
                        "Peer unavailable: " + peer.getId()
                );
            }
        }

        return results;
    }

    public String searchAndAggregate(String path) {
        List<String> responses = searchAllPeers(path);

        JsonResponseAggregator aggregator =
                new JsonResponseAggregator();

        return aggregator.aggregate(responses);
    }

    public String processSearch(DistributedSearchRequest request) {

        Objects.requireNonNull(request, "Request cannot be null");

        if (!tracker.markIfNew(request.getRequestId())) {
            return "[]";
        }

        return searchAndAggregate(request.getPath());
    }


}
