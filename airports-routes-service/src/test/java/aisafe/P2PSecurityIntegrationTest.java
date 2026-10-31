
package aisafe;

import aisafe.p2p.PeerIdentity;
import aisafe.p2p.PeerTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "security.p2p.jwt.secret=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "security.p2p.jwt.expiration-ms=300000",
        "p2p.peer-id=peer-test"


})
@AutoConfigureMockMvc
@ActiveProfiles("jpa")
class P2PSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PeerTokenService peerTokenService;

    @Test
    void shouldRejectRequestWithoutToken() throws Exception {
        mockMvc.perform(post("/api/p2p/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "test-1",
                                  "path": "/api/airports",
                                  "ttl": 2
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAcceptValidPeerToken() throws Exception {
        String token = peerTokenService.generateToken(
                new PeerIdentity("peer-1")
        );

        mockMvc.perform(post("/api/p2p/search")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestId": "test-2",
                                  "path": "/api/airports",
                                  "ttl": 2
                                }
                                """))
                .andExpect(status().isOk());
    }
}
