package aisafe.flights.infrastructure.persistence.jpa;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelUtilizationProjectionTest {

    @Test
    void shouldReturnCorrectValuesFromProjection() {
        ModelUtilizationProjection projection = new ModelUtilizationProjection() {
            @Override
            public String getModelName() {
                return "CS-TRP";
            }

            @Override
            public Long getUtilizationValue() {
                return 150L;
            }
        };

        assertEquals("CS-TRP", projection.getModelName());
        assertEquals(150L, projection.getUtilizationValue());
    }
}