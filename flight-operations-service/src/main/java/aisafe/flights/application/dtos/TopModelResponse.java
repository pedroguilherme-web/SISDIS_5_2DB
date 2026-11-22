package aisafe.flights.application.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TopModelResponse(String modelName, Number value) {
    public double valueAsDouble() {
        return value != null ? value.doubleValue() : 0.0;
    }

    public TopModelResponse rounded() {
        return new TopModelResponse(modelName, Math.round(valueAsDouble()));
    }
}
