package aisafe.flights.infrastructure.persistence.jpa;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RouteUtilizationProjectionTest {

    @Test
    void shouldReturnCorrectValuesFromProjection() {
        // Criar uma implementação anónima da interface
        RouteUtilizationProjection projection = new RouteUtilizationProjection() {
            @Override
            public Long getRouteId() { return 10L; }

            @Override
            public String getOriginCode() { return "LIS"; }

            @Override
            public String getDestinationCode() { return "OPO"; }

            @Override
            public Long getFlightCount() { return 25L; }
        };

        assertEquals(10L, projection.getRouteId());
        assertEquals("LIS", projection.getOriginCode());
        assertEquals("OPO", projection.getDestinationCode());
        assertEquals(25L, projection.getFlightCount());
    }
}