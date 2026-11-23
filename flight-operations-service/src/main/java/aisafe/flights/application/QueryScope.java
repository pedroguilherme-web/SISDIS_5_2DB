package aisafe.flights.application;

public enum QueryScope {
    CLUSTER,
    LOCAL;

    public boolean includesPeers() {
        return this == CLUSTER;
    }
}
