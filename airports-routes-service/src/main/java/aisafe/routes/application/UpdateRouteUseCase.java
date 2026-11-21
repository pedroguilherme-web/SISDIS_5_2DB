package aisafe.routes.application;

import aisafe.shared.application.UseCase;
import aisafe.airports.domain.IataCode;
import aisafe.routes.application.dtos.RouteResponse;
import aisafe.routes.application.dtos.UpdateRouteRequest;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteHistory;
import aisafe.routes.domain.RouteHistoryRepository;
import aisafe.routes.domain.RouteNotFoundException;
import aisafe.routes.domain.RouteRepository;
import aisafe.routes.domain.RouteStatus;
import aisafe.shared.domain.ConcurrencyException;
import aisafe.routes.infrastructure.messaging.RouteEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.Optional;

/**
 * Use case responsible for updating the details of an existing route.
 */
@UseCase
public class UpdateRouteUseCase {

    private final RouteRepository routeRepository;
    private final RouteHistoryRepository routeHistoryRepository;
    private final Optional<RouteEventPublisher> eventPublisher;

    public UpdateRouteUseCase(RouteRepository routeRepository, RouteHistoryRepository routeHistoryRepository) {
        this(routeRepository, routeHistoryRepository, Optional.empty());
    }

    @Autowired
    public UpdateRouteUseCase(
            RouteRepository routeRepository,
            RouteHistoryRepository routeHistoryRepository,
            Optional<RouteEventPublisher> eventPublisher) {
        this.routeRepository = routeRepository;
        this.routeHistoryRepository = routeHistoryRepository;
        this.eventPublisher = eventPublisher == null ? Optional.empty() : eventPublisher;
    }

    /**
     * Updates the route details and records the change in the history.
     *
     * @param origin        the IATA code of the origin airport
     * @param destination   the IATA code of the destination airport
     * @param request       the data to update
     * @param clientVersion the version for optimistic locking
     * @param changedBy     the identifier of the user making the update
     * @return the updated route response
     */
    public RouteResponse execute(String origin, String destination, UpdateRouteRequest request, Long clientVersion, String changedBy) {
        Route route = routeRepository.findByOriginAndDestination(new IataCode(origin), new IataCode(destination))
                .orElseThrow(() -> new RouteNotFoundException(origin + "-" + destination));

        if (clientVersion != null) {
            Long currentVersion = routeRepository.findVersionFor(new IataCode(origin), new IataCode(destination));
            if (!Objects.equals(currentVersion, clientVersion)) {
                throw new ConcurrencyException("Route version mismatch. Please fetch the latest version and retry.");
            }
        }

        route.updateRoute(
                request.estimatedFlightTime(),
                request.minimumRange(),
                request.minimumCapacity()
        );

        if (request.active() != null) {
            route.changeStatus(request.active() ? RouteStatus.ACTIVE : RouteStatus.INACTIVE);
        }

        routeRepository.save(route);
        routeHistoryRepository.save(new RouteHistory(new IataCode(origin), new IataCode(destination), "Route details updated", changedBy));

        Long updatedVersion = routeRepository.findVersionFor(new IataCode(origin), new IataCode(destination));
        RouteResponse response = RouteResponse.from(route, updatedVersion);
        eventPublisher.ifPresent(publisher -> publisher.publish(response, "RouteUpdated"));
        return response;
    }
}
