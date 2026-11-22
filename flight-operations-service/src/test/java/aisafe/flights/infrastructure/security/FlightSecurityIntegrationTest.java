package aisafe.flights.infrastructure.security;

import aisafe.security.application.JwtService;
import aisafe.security.domain.Role;
import aisafe.security.domain.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"aisafe.messaging.enabled=false", "aisafe.read-model.rebuild-on-startup=false"})
@AutoConfigureMockMvc
@ActiveProfiles("jpa")
class FlightSecurityIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    private String bearer(Role role) {
        return "Bearer " + jwtService.generateToken(new User("test-" + role.name().toLowerCase(), "unused", role));
    }

    private static RequestPostProcessor from(String remoteAddress) {
        return request -> {
            request.setRemoteAddr(remoteAddress);
            return request;
        };
    }

    @Test
    void serviceCallFromTheTrustedNetworkNeedsNoToken() throws Exception {
        mockMvc.perform(get("/api/flights/aircraft/CS-TPA/hours"))
                .andExpect(status().isOk())
                .andExpect(content().string("0.0"));
        mockMvc.perform(get("/api/flights/count").param("origin", "LIS").param("destination", "OPO"))
                .andExpect(status().isOk())
                .andExpect(content().string("0"));
    }

    @Test
    void serviceCallFromOutsideTheTrustedNetworkWithoutTokenIsRejected() throws Exception {
        mockMvc.perform(get("/api/flights/aircraft/CS-TPA/exists").with(from("10.1.2.3")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userWithAnOperationalRoleCanReadStatisticsFromAnywhere() throws Exception {
        mockMvc.perform(get("/api/flights/top-models/hours").with(from("10.1.2.3"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.MAINTENANCE_SUPERVISOR)))
                .andExpect(status().isOk());
    }

    @Test
    void changingStatusRequiresAToken() throws Exception {
        mockMvc.perform(patch("/api/flights/any/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changingStatusRequiresTheAtccRole() throws Exception {
        mockMvc.perform(patch("/api/flights/any/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELED\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BACKOFFICE_OPERATOR)))
                .andExpect(status().isForbidden());
    }

    @Test
    void atccAndAdminCanChangeStatus() throws Exception {
        mockMvc.perform(patch("/api/flights/unknown/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELED\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ATCC)))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/flights/unknown/status").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CANCELED\"}")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)))
                .andExpect(status().isNotFound());
    }

    @Test
    void schedulingStillFollowsTheSharedRules() throws Exception {
        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/flights").param("aircraftId", "CS-TPA")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.MAINTENANCE_TECHNICIAN)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unicornsArePublicAsRequiredByTheAssignment() throws Exception {
        mockMvc.perform(get("/api/v2/unicorns").with(from("10.1.2.3")))
                .andExpect(status().isOk());
    }

    @Test
    void readModelQueriesNeedAnOperationalRole() throws Exception {
        mockMvc.perform(get("/api/v2/flights"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v2/flights").header(HttpHeaders.AUTHORIZATION, bearer(Role.MAINTENANCE_TECHNICIAN)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v2/flights/unknown").header(HttpHeaders.AUTHORIZATION, bearer(Role.ATCC)))
                .andExpect(status().isNotFound());
    }

    @Test
    void onlyAnAdminCanRebuildTheReadModel() throws Exception {
        mockMvc.perform(post("/api/v2/flights/read-model/rebuild").header(HttpHeaders.AUTHORIZATION, bearer(Role.ATCC)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v2/flights/read-model/rebuild").header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)))
                .andExpect(status().isOk());
    }

    @Test
    void aBackofficeOperatorCanOnlyScheduleThroughAForwardedImportRowFromTheTrustedNetwork() throws Exception {
        String body = "{}";
        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BACKOFFICE_OPERATOR)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BACKOFFICE_OPERATOR))
                        .header("X-Peer-Forwarded", "true").with(from("10.1.2.3")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.BACKOFFICE_OPERATOR))
                        .header("X-Peer-Forwarded", "true"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/flights").contentType(MediaType.APPLICATION_JSON).content(body)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ATCC)))
                .andExpect(status().isBadRequest());
    }
}
