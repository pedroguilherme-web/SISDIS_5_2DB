package aisafe.aircrafts.infrastructure;

import aisafe.aircrafts.cqrs.projections.AircraftReadModel;
import aisafe.aircrafts.cqrs.projections.AircraftReadModelRepository;
import aisafe.aircrafts.domain.AircraftRepository;
import aisafe.shared.infrastructure.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Proxy;
import java.util.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("FleetReadModelController Tests")
class FleetReadModelControllerTest {

    private MockMvc mockMvc;
    private Map<String, AircraftReadModel> storage;
    private AircraftReadModelRepository readModelRepository;
    private AircraftRepository aircraftRepository;

    @BeforeEach
    void setUp() {
        storage = new HashMap<>();
        readModelRepository = (AircraftReadModelRepository) Proxy.newProxyInstance(
                AircraftReadModelRepository.class.getClassLoader(),
                new Class<?>[]{AircraftReadModelRepository.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("findByRegistrationNumber".equals(name) || "findById".equals(name)) {
                        return Optional.ofNullable(storage.get(args[0]));
                    } else if ("save".equals(name)) {
                        AircraftReadModel rm = (AircraftReadModel) args[0];
                        storage.put(rm.getRegistrationNumber(), rm);
                        return rm;
                    } else if ("findAll".equals(name)) {
                        return new ArrayList<>(storage.values());
                    } else if ("count".equals(name)) {
                        return (long) storage.size();
                    }
                    return null;
                }
        );

        aircraftRepository = (AircraftRepository) Proxy.newProxyInstance(
                AircraftRepository.class.getClassLoader(),
                new Class<?>[]{AircraftRepository.class},
                (proxy, method, args) -> {
                    if ("findAll".equals(method.getName())) {
                        return Collections.emptyList();
                    }
                    return null;
                }
        );

        FleetReadModelController controller = new FleetReadModelController(readModelRepository, aircraftRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v2/fleet - should return operational readiness list for all aircraft")
    void shouldReturnReadinessListForAllAircraft() throws Exception {
        AircraftReadModel a1 = new AircraftReadModel("CS-TKA", "Airbus A320neo", "AVAILABLE", 50.0, 0);
        AircraftReadModel a2 = new AircraftReadModel("CS-TKB", "Boeing 737 MAX", "UNDER_MAINTENANCE", 120.0, 1);
        storage.put("CS-TKA", a1);
        storage.put("CS-TKB", a2);

        mockMvc.perform(get("/api/v2/fleet")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[?(@.registrationNumber == 'CS-TKA')].readinessStatus").value("READY_FOR_FLIGHT"))
                .andExpect(jsonPath("$[?(@.registrationNumber == 'CS-TKB')].readinessStatus").value("UNDER_MAINTENANCE"));
    }

    @Test
    @DisplayName("GET /api/v2/fleet/{registration} - should return specific aircraft readiness projection")
    void shouldReturnSpecificAircraftReadiness() throws Exception {
        AircraftReadModel a1 = new AircraftReadModel("CS-TKA", "Airbus A320neo", "AVAILABLE", 50.0, 0);
        storage.put("CS-TKA", a1);

        mockMvc.perform(get("/api/v2/fleet/CS-TKA")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.registrationNumber").value("CS-TKA"))
                .andExpect(jsonPath("$.readinessStatus").value("READY_FOR_FLIGHT"));
    }

    @Test
    @DisplayName("GET /api/v2/fleet/{registration} - should return 404 when aircraft not found")
    void shouldReturn404WhenNotFound() throws Exception {
        mockMvc.perform(get("/api/v2/fleet/NON-EXISTENT")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
