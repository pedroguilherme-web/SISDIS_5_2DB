
package aisafe.p2p;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class JsonResponseAggregator {

    private final ObjectMapper mapper = new ObjectMapper();

    public String aggregate(List<String> responses) {

        if (responses == null) {
            throw new IllegalArgumentException(
                    "Responses cannot be null"
            );
        }

        Set<JsonNode> uniqueResults = new LinkedHashSet<>();

        for (String response : responses) {
            if (response == null || response.isBlank()) {
                continue;
            }

            try {
                JsonNode root = mapper.readTree(response);

                if (!root.isArray()) {
                    throw new IllegalArgumentException(
                            "Peer response must be a JSON array"
                    );
                }

                for (JsonNode item : root) {
                    uniqueResults.add(item);
                }

            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                throw new IllegalArgumentException(
                        "Invalid JSON response from peer", e
                );
            }
        }

        ArrayNode result = mapper.createArrayNode();

        for (JsonNode item : uniqueResults) {
            result.add(item);
        }

        return result.toString();
    }
}
