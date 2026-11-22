package aisafe.flights.infrastructure.http;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class JsonBodies {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private JsonBodies() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (IOException e) {
            throw new IllegalStateException("Could not serialise " + value.getClass().getSimpleName(), e);
        }
    }

    public static <T> T read(String body, Class<T> type) {
        try {
            return MAPPER.readValue(body, type);
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected response body for " + type.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public static <T> List<T> readList(String body, Class<T> itemType) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode items = findItems(MAPPER.readTree(body));
            List<T> result = new ArrayList<>();
            if (items != null) {
                for (JsonNode item : items) {
                    result.add(MAPPER.treeToValue(item, itemType));
                }
            }
            return result;
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected list body for " + itemType.getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    public static String errorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            JsonNode node = MAPPER.readTree(body);
            return node.hasNonNull("message") ? node.get("message").asText() : body;
        } catch (IOException e) {
            return body;
        }
    }

    private static JsonNode findItems(JsonNode root) {
        if (root.isArray()) {
            return root;
        }
        if (root.has("_embedded")) {
            Iterator<JsonNode> values = root.get("_embedded").elements();
            while (values.hasNext()) {
                JsonNode candidate = values.next();
                if (candidate.isArray()) {
                    return candidate;
                }
            }
            return null;
        }
        for (String field : new String[]{"content", "data"}) {
            if (root.has(field) && root.get(field).isArray()) {
                return root.get(field);
            }
        }
        return null;
    }
}
