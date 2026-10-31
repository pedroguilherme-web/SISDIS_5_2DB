
package aisafe.p2p;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JsonResponseAggregatorTest {

    private final JsonResponseAggregator aggregator =
            new JsonResponseAggregator();

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void shouldMergeResponsesAndRemoveDuplicates()
            throws Exception {

        List<String> responses = List.of(
                "[{\"code\":\"OPO\"},{\"code\":\"LIS\"}]",
                "[{\"code\":\"LIS\"},{\"code\":\"FAO\"}]"
        );

        String result = aggregator.aggregate(responses);

        JsonNode json = mapper.readTree(result);

        assertEquals(3, json.size());
        assertEquals("OPO", json.get(0).get("code").asText());
        assertEquals("LIS", json.get(1).get("code").asText());
        assertEquals("FAO", json.get(2).get("code").asText());
    }

    @Test
    void shouldIgnoreEmptyResponses() throws Exception {

        List<String> responses = List.of(
                "",
                "[]",
                "[{\"code\":\"OPO\"}]"
        );

        String result = aggregator.aggregate(responses);

        JsonNode json = mapper.readTree(result);

        assertEquals(1, json.size());
        assertEquals("OPO", json.get(0).get("code").asText());
    }

    @Test
    void shouldRejectInvalidJson() {

        assertThrows(
                IllegalArgumentException.class,
                () -> aggregator.aggregate(
                        List.of("invalid json")
                )
        );
    }
}
