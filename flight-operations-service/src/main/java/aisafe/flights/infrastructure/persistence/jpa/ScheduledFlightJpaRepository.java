package aisafe.flights.infrastructure.persistence.jpa;

import aisafe.flights.domain.ModelUtilizationData;
import aisafe.flights.domain.RouteUtilizationData;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
@Profile("jpa")
public class ScheduledFlightJpaRepository implements ScheduledFlightRepository {
    private final SpringDataScheduledFlightRepository springRepo;
    private final AircraftScheduleLocks lockRows;
    private final AircraftScheduleLockRepository locks;

    public ScheduledFlightJpaRepository(SpringDataScheduledFlightRepository springRepo,
                                        AircraftScheduleLocks lockRows,
                                        AircraftScheduleLockRepository locks) {
        this.springRepo = springRepo;
        this.lockRows = lockRows;
        this.locks = locks;
    }

    @Override
    public void lockAircraftForScheduling(String registration) {
        // SELECT ... FOR UPDATE on the aircraft's row: a second schedule for the same aircraft waits until this transaction commits.
        lockRows.ensureLockRow(registration);
        locks.lockForUpdate(registration)
                .orElseThrow(() -> new IllegalStateException("Lock row missing for aircraft " + registration));
    }

    @Override
    public ScheduledFlight save(ScheduledFlight flight) {
        ScheduledFlightJpaEntity entity = springRepo.findById(flight.getId())
                .map(existing -> {
                    ScheduledFlightMapper.copyMutableState(flight, existing);
                    return existing;
                })
                .orElseGet(() -> ScheduledFlightMapper.toNewEntity(flight));
        return ScheduledFlightMapper.toDomain(springRepo.save(entity));
    }

    @Override
    public void delete(ScheduledFlight flight) {
        if (flight.getId() != null) {
            springRepo.deleteById(flight.getId());
        }
    }

    @Override
    public Optional<ScheduledFlight> findById(String id) {
        return springRepo.findById(id).map(ScheduledFlightMapper::toDomain);
    }

    @Override
    public List<ScheduledFlight> findAll() {
        return springRepo.findAll().stream().map(ScheduledFlightMapper::toDomain).toList();
    }

    @Override
    public long count() {
        return springRepo.count();
    }

    @Override
    public List<ScheduledFlight> findByAircraftRegistration(String registration) {
        return springRepo.findByAircraftRegistration(registration).stream()
                .map(ScheduledFlightMapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsByOverlappingSchedule(String registration, OffsetDateTime departureDateTime, OffsetDateTime arrivalDateTime) {
        return springRepo.hasOverlappingFlights(registration, departureDateTime, arrivalDateTime);
    }

    @Override
    public boolean existsByAircraftRegistration(String registration) {
        return springRepo.existsByAircraftRegistrationNumber(registration);
    }

    @Override
    public long countByRoute(String origin, String destination) {
        return springRepo.countByRoute(origin, destination);
    }

    @Override
    public List<ModelUtilizationData> findModelUtilizationByFlightHours() {
        return toModelData(springRepo.findModelUtilizationByFlightHours());
    }

    @Override
    public List<ModelUtilizationData> findModelUtilizationByAssignments() {
        return toModelData(springRepo.findModelUtilizationByAssignments());
    }

    @Override
    public double calculateTotalOperationalHoursByRegistration(String registration) {
        Double hours = springRepo.calculateTotalOperationalHoursByRegistration(registration);
        return hours != null ? hours : 0.0;
    }

    @Override
    public double calculateOperationalHoursSince(String registration, OffsetDateTime sinceDate) {
        Double hours = springRepo.calculateOperationalHoursSince(registration, sinceDate);
        return hours != null ? hours : 0.0;
    }

    @Override
    public List<RouteUtilizationData> findRouteUtilization(OffsetDateTime startDate, OffsetDateTime endDate) {
        return springRepo.findRouteUtilization(startDate, endDate).stream()
                .map(p -> new RouteUtilizationData(p.getOriginCode(), p.getDestinationCode(), p.getFlightCount()))
                .toList();
    }

    private static List<ModelUtilizationData> toModelData(List<ModelUtilizationProjection> rows) {
        return rows.stream()
                .map(p -> new ModelUtilizationData(p.getModelName(),
                        p.getUtilizationValue() != null ? p.getUtilizationValue().doubleValue() : 0.0))
                .toList();
    }
}
