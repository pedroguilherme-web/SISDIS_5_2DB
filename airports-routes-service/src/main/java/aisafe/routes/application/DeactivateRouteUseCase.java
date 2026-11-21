package aisafe.routes.application;

import aisafe.shared.application.UseCase;
import aisafe.airports.domain.IataCode;
import aisafe.routes.application.dtos.RouteResponse;
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

@UseCase
public class DeactivateRouteUseCase {

    private final RouteRepository routeRepository;
    private final RouteHistoryRepository routeHistoryRepository;
    private final Optional<RouteEventPublisher> eventPublisher;

    public DeactivateRouteUseCase(RouteRepository routeRepository, RouteHistoryRepository routeHistoryRepository) {
        this(routeRepository, routeHistoryRepository, Optional.empty());
    }

    @Autowired
    public DeactivateRouteUseCase(
            RouteRepository routeRepository,
            RouteHistoryRepository routeHistoryRepository,
            Optional<RouteEventPublisher> eventPublisher) {
        this.routeRepository = routeRepository;
        this.routeHistoryRepository = routeHistoryRepository;
        this.eventPublisher = eventPublisher == null ? Optional.empty() : eventPublisher;
    }

    public RouteResponse execute(String origin, String destination, Long clientVersion, String changedBy) {
        Route route = routeRepository.findByOriginAndDestination(new IataCode(origin), new IataCode(destination))
                .orElseThrow(() -> new RouteNotFoundException(origin + "-" + destination));

        if (clientVersion != null) {
            Long currentVersion = routeRepository.findVersionFor(new IataCode(origin), new IataCode(destination));
            if (!Objects.equals(currentVersion, clientVersion)) {
                throw new ConcurrencyException("Route version mismatch. Please fetch the latest version and retry.");
            }
        }

        route.changeStatus(RouteStatus.INACTIVE);
        routeRepository.save(route);
        routeHistoryRepository.save(new RouteHistory(new IataCode(origin), new IataCode(destination), "Route deactivated", changedBy));
        
        Long updatedVersion = routeRepository.findVersionFor(new IataCode(origin), new IataCode(destination));
        RouteResponse response = RouteResponse.from(route, updatedVersion);
        eventPublisher.ifPresent(publisher -> publisher.publish(response, "RouteDeactivated"));
        return response;
    }
}
