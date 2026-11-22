package aisafe.flights.domain;

import java.util.EnumSet;
import java.util.Set;

public enum FlightStatus {
    SCHEDULED,
    DELAYED,
    IN_FLIGHT,
    COMPLETED,
    CANCELED;

    public Set<FlightStatus> allowedTransitions() {
        return switch (this) {
            case SCHEDULED -> EnumSet.of(DELAYED, IN_FLIGHT, CANCELED);
            case DELAYED -> EnumSet.of(IN_FLIGHT, CANCELED);
            case IN_FLIGHT -> EnumSet.of(COMPLETED);
            case COMPLETED, CANCELED -> EnumSet.noneOf(FlightStatus.class);
        };
    }

    public boolean canTransitionTo(FlightStatus target) {
        return target != null && allowedTransitions().contains(target);
    }

    public boolean isTerminal() {
        return allowedTransitions().isEmpty();
    }
}
