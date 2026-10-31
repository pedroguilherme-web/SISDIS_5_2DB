
package aisafe.p2p;

import java.util.Objects;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class DistributedSearchRequest {

    private final String requestId;
    private final String path;
    private final int ttl;


    @JsonCreator
    public DistributedSearchRequest(
            @JsonProperty("requestId") String requestId,
            @JsonProperty("path") String path,
            @JsonProperty("ttl") int ttl) {


        this.requestId = Objects.requireNonNull(
                requestId, "Request ID is required"
        );

        this.path = Objects.requireNonNull(
                path, "Search path is required"
        );

        if (!path.startsWith("/") || path.startsWith("//")) {
            throw new IllegalArgumentException(
                    "Path must start with a single /"
            );
        }

        if (ttl < 0) {
            throw new IllegalArgumentException(
                    "TTL cannot be negative"
            );
        }

        this.ttl = ttl;
    }

    public static DistributedSearchRequest create(
            String path, int ttl) {

        return new DistributedSearchRequest(
                UUID.randomUUID().toString(),
                path,
                ttl
        );
    }

    public DistributedSearchRequest nextHop() {
        if (ttl == 0) {
            throw new IllegalStateException(
                    "TTL exhausted"
            );
        }

        return new DistributedSearchRequest(
                requestId, path, ttl - 1
        );
    }

    public String getRequestId() {
        return requestId;
    }

    public String getPath() {
        return path;
    }

    public int getTtl() {
        return ttl;
    }
}
