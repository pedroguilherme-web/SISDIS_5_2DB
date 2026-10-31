
package aisafe.p2p;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SearchRequestTracker {

    private final Set<String> processedRequests =
            ConcurrentHashMap.newKeySet();

    public boolean markIfNew(String requestId) {

        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException(
                    "Request ID cannot be empty"
            );
        }

        return processedRequests.add(requestId);
    }

    public boolean hasProcessed(String requestId) {
        return processedRequests.contains(requestId);
    }

    public int getProcessedCount() {
        return processedRequests.size();
    }
}
