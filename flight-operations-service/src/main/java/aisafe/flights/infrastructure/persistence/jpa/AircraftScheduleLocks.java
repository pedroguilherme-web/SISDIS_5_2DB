package aisafe.flights.infrastructure.persistence.jpa;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AircraftScheduleLocks {
    private final AircraftScheduleLockRepository locks;

    public AircraftScheduleLocks(AircraftScheduleLockRepository locks) {
        this.locks = locks;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void ensureLockRow(String registration) {
        locks.ensureExists(registration);
    }
}
