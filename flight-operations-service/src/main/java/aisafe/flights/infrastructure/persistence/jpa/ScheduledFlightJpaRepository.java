package aisafe.flights.infrastructure.persistence.jpa;

import aisafe.flights.domain.RouteUtilizationData;
import aisafe.flights.domain.ModelUtilizationData;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import aisafe.shared.domain.PaginatedResult;
import org.springframework.stereotype.Repository;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@Profile("jpa")
public class ScheduledFlightJpaRepository implements ScheduledFlightRepository {

    private final SpringDataScheduledFlightRepository springRepo;

    // Repositórios de Aircraft e Route removidos (desacoplamento concluído!)
    public ScheduledFlightJpaRepository(SpringDataScheduledFlightRepository springRepo) {
        this.springRepo = springRepo;
    }

    @Override
    public List<ModelUtilizationData> findTopModelsByFlightHours(int limit) {
        return springRepo.findTopModelsByFlightHours(PageRequest.of(0, limit)).stream()
                .map(p -> new ModelUtilizationData(p.getModelName(), p.getUtilizationValue()))
                .toList();
    }

    @Override
    public List<ModelUtilizationData> findTopModelsByAssignments(int limit) {
        return springRepo.findTopModelsByAssignments(PageRequest.of(0, limit)).stream()
                .map(p -> new ModelUtilizationData(p.getModelName(), p.getUtilizationValue()))
                .toList();
    }

    @Override
    public ScheduledFlight save(ScheduledFlight flight) {
        // Já não precisas de ir buscar o RouteEntity ou AircraftEntity!
        ScheduledFlightJpaEntity entity = new ScheduledFlightJpaEntity(
                flight.getDepartureDateTime(),
                flight.getArrivalDateTime(),
                flight.getStatus(),
                flight.getOriginCode(),
                flight.getDestinationCode(),
                flight.getAircraftRegistrationNumber()
        );
        entity.setId(flight.getId());

        ScheduledFlightJpaEntity saved = springRepo.save(entity);
        return ScheduledFlightMapper.toDomain(saved);
    }

    @Override
    public void delete(ScheduledFlight flight) {
        if (flight.getId() != null) {
            springRepo.deleteById(flight.getId());
        }
    }

    @Override
    public Optional<ScheduledFlight> findById(Long id) {
        return springRepo.findById(id).map(ScheduledFlightMapper::toDomain);
    }

    @Override
    public List<ScheduledFlight> findAll() {
        return springRepo.findAll().stream()
                .map(ScheduledFlightMapper::toDomain)
                .collect(Collectors.toList());
    }

    // Assinaturas adaptadas para receber String em vez de Value Objects
    @Override
    public List<ScheduledFlight> findByAircraftRegistration(String registration) {
        return springRepo.findByAircraftRegistration(registration).stream()
                .map(ScheduledFlightMapper::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    public long count() {
        return springRepo.count();
    }

    @Override
    public List<ScheduledFlight> findFlightsForUtilization(String registration, OffsetDateTime start, OffsetDateTime end) {
        return springRepo.findFlightsForUtilization(registration, start, end).stream()
                .map(ScheduledFlightMapper::toDomain)
                .collect(Collectors.toList());
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
    public Double calculateTotalOperationalHoursByRegistration(String registration) {
        return springRepo.calculateTotalOperationalHoursByRegistration(registration);
    }

    @Override
    public Double calculateOperationalHoursSince(String registration, OffsetDateTime sinceDate) {
        return springRepo.calculateOperationalHoursSince(registration, sinceDate);
    }

    @Override
    public PaginatedResult<RouteUtilizationData> getFlightUtilizationReport(OffsetDateTime startDate, OffsetDateTime endDate, int page, int size) {
        Page<RouteUtilizationProjection> resultPage = springRepo.findFlightUtilizationReports(startDate, endDate, PageRequest.of(page, size));
        List<RouteUtilizationData> data = resultPage.stream()
                .map(p -> new RouteUtilizationData(
                        p.getRouteId(),
                        p.getOriginCode(),
                        p.getDestinationCode(),
                        p.getFlightCount()
                ))
                .collect(Collectors.toList());
        return new PaginatedResult<>(data, resultPage.getTotalElements());
    }
}