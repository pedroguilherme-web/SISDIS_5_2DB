package aisafe.routes.application;

import aisafe.shared.application.UseCase;
import aisafe.airports.domain.IataCode;
import aisafe.routes.domain.Route;
import aisafe.routes.domain.RouteHistoryRepository;
import aisafe.routes.domain.RouteNotFoundException;
import aisafe.routes.domain.RouteRepository;
import aisafe.routes.application.dtos.RouteResponse;
import aisafe.routes.infrastructure.messaging.RouteEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;

@UseCase
public class DeleteRouteUseCase {
    private final RouteRepository routeRepository;
    private final RouteHistoryRepository routeHistoryRepository;
    private final Optional<RouteEventPublisher> eventPublisher;

    public DeleteRouteUseCase(RouteRepository routeRepository, RouteHistoryRepository routeHistoryRepository) {
        this(routeRepository, routeHistoryRepository, Optional.empty());
    }

    @Autowired
    public DeleteRouteUseCase(
            RouteRepository routeRepository,
            RouteHistoryRepository routeHistoryRepository,
            Optional<RouteEventPublisher> eventPublisher) {
        this.routeRepository = routeRepository;
        this.routeHistoryRepository = routeHistoryRepository;
        this.eventPublisher = eventPublisher == null ? Optional.empty() : eventPublisher;
    }

    public void execute(String origin, String destination) {
        Route route = routeRepository.findByOriginAndDestination(new IataCode(origin), new IataCode(destination))
                .orElseThrow(() -> new RouteNotFoundException(origin + "-" + destination));
        routeHistoryRepository.deleteAllByRoute(origin, destination);
        routeRepository.delete(route);
        eventPublisher.ifPresent(publisher ->
                publisher.publish(RouteResponse.from(route), "RouteDeleted"));
    }
}
