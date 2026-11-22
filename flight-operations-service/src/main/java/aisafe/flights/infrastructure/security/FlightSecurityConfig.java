package aisafe.flights.infrastructure.security;

import aisafe.flights.infrastructure.PeerForwarding;
import aisafe.security.infrastructure.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
public class FlightSecurityConfig {
    static final String[] INTER_SERVICE_PATHS = {
            "/api/flights/aircraft/**",
            "/api/flights/top-models/**",
            "/api/flights/count"
    };

    @Bean
    @Order(2)
    public SecurityFilterChain flightOperationsFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthFilter,
            AuthenticationProvider authenticationProvider,
            RoleHierarchy roleHierarchy,
            @Value("${aisafe.security.trusted-networks:127.0.0.1/32,::1/128}") List<String> trustedNetworks) throws Exception {
        AuthorityAuthorizationManager<RequestAuthorizationContext> operationalRoles = AuthorityAuthorizationManager.hasAnyRole(
                "ATCC", "BACKOFFICE_OPERATOR", "MAINTENANCE_TECHNICIAN", "MAINTENANCE_SUPERVISOR");
        operationalRoles.setRoleHierarchy(roleHierarchy);

        AuthorizationManager<RequestAuthorizationContext> userOrTrustedService = AuthorizationManagers.anyOf(
                operationalRoles, new TrustedNetworkAuthorizationManager(trustedNetworks));

        AuthorityAuthorizationManager<RequestAuthorizationContext> atcc = AuthorityAuthorizationManager.hasRole("ATCC");
        atcc.setRoleHierarchy(roleHierarchy);
        AuthorityAuthorizationManager<RequestAuthorizationContext> backoffice = AuthorityAuthorizationManager.hasRole("BACKOFFICE_OPERATOR");
        backoffice.setRoleHierarchy(roleHierarchy);

        AuthorizationManager<RequestAuthorizationContext> scheduleFlight = AuthorizationManagers.anyOf(atcc,
                AuthorizationManagers.allOf(backoffice, new TrustedNetworkAuthorizationManager(trustedNetworks),
                        (authentication, context) -> new org.springframework.security.authorization.AuthorizationDecision(
                                "true".equalsIgnoreCase(context.getRequest().getHeader(PeerForwarding.HEADER)))));

        http
                .securityMatchers(matchers -> matchers
                        .requestMatchers(HttpMethod.POST, "/api/flights")
                        .requestMatchers(matchedPaths()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/flights").access(scheduleFlight)
                        .requestMatchers(HttpMethod.PATCH, "/api/flights/*/status").hasRole("ATCC")
                        .requestMatchers(HttpMethod.GET, INTER_SERVICE_PATHS).access(userOrTrustedService)

                        .requestMatchers(HttpMethod.GET, "/api/v2/unicorns", "/api/v2/unicorns/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v2/flights/read-model/rebuild").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v2/flights", "/api/v2/flights/**").access(operationalRoles)
                        .anyRequest().denyAll())
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private static String[] matchedPaths() {
        List<String> paths = new java.util.ArrayList<>(List.of(INTER_SERVICE_PATHS));
        paths.add("/api/flights/*/status");
        paths.add("/api/v2/flights");
        paths.add("/api/v2/flights/**");
        paths.add("/api/v2/unicorns");
        paths.add("/api/v2/unicorns/**");
        return paths.toArray(String[]::new);
    }
}
