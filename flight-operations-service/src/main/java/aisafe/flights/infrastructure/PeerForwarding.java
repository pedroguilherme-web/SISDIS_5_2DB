package aisafe.flights.infrastructure;

import aisafe.flights.application.QueryScope;

public final class PeerForwarding {
    public static final String HEADER = "X-Peer-Forwarded";

    private PeerForwarding() {
    }

    public static QueryScope scopeOf(String headerValue) {
        return "true".equalsIgnoreCase(headerValue) ? QueryScope.LOCAL : QueryScope.CLUSTER;
    }
}
