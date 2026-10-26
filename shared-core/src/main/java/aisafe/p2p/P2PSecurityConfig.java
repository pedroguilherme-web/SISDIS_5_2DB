
package aisafe.p2p;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class P2PSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain p2pSecurityFilterChain(
            HttpSecurity http,
            PeerTokenService peerTokenService
    ) throws Exception {

        return http
                .securityMatcher("/api/p2p/**")
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/p2p/search")
                        .hasRole("PEER")
                        .anyRequest().denyAll())
                .addFilterBefore(
                        new PeerAuthenticationFilter(peerTokenService),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
