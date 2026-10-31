
package aisafe.p2p;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PeerAuthenticationFilterTest {

    @Test
    void shouldAuthenticateValidPeerToken() throws Exception {
        byte[] key = new byte[32];
        Arrays.fill(key, (byte) 7);

        PeerTokenService tokenService = new PeerTokenService(
                Base64.getEncoder().encodeToString(key),
                300000
        );

        String token = tokenService.generateToken(
                new PeerIdentity("peer-1")
        );

        PeerAuthenticationFilter filter =
                new PeerAuthenticationFilter(tokenService);

        MockHttpServletRequest request =
                new MockHttpServletRequest("POST", "/api/p2p/search");

        request.setServletPath("/api/p2p/search");
        request.addHeader("Authorization", "Bearer " + token);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain chain = mock(FilterChain.class);

        try {
            filter.doFilter(request, response, chain);

            var authentication =
                    SecurityContextHolder.getContext().getAuthentication();

            assertNotNull(authentication);
            assertEquals("peer-1", authentication.getName());

            assertTrue(authentication.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_PEER")));

            verify(chain).doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
