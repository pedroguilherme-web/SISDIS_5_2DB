
package aisafe.p2p;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class PeerAuthenticationFilter extends OncePerRequestFilter {

    private final PeerTokenService peerTokenService;

    public PeerAuthenticationFilter(PeerTokenService peerTokenService) {
        this.peerTokenService = peerTokenService;
    }


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals("/api/p2p/search")
                || !request.getMethod().equals("POST");
    }


    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);

            if (peerTokenService.isTokenValid(token)) {
                var authentication =
                        new UsernamePasswordAuthenticationToken(
                                peerTokenService.extractPeerId(token),
                                null,
                                List.of(new SimpleGrantedAuthority("ROLE_PEER"))
                        );

                SecurityContextHolder.getContext()
                        .setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
