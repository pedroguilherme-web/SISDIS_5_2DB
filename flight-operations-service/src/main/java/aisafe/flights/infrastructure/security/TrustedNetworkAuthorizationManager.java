package aisafe.flights.infrastructure.security;

import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.util.matcher.IpAddressMatcher;

import java.util.List;
import java.util.function.Supplier;

public class TrustedNetworkAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {
    private final List<IpAddressMatcher> trustedNetworks;

    public TrustedNetworkAuthorizationManager(List<String> cidrs) {
        this.trustedNetworks = cidrs.stream()
                .map(String::trim)
                .filter(cidr -> !cidr.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication, RequestAuthorizationContext context) {
        String remoteAddress = context.getRequest().getRemoteAddr();
        boolean trusted = trustedNetworks.stream().anyMatch(network -> network.matches(remoteAddress));
        return new AuthorizationDecision(trusted);
    }
}
