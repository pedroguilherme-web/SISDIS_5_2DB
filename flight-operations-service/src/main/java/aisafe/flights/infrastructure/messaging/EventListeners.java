package aisafe.flights.infrastructure.messaging;

import aisafe.flights.application.events.FlightEvent;
import aisafe.flights.infrastructure.catalog.CatalogUpdater;
import aisafe.flights.infrastructure.messaging.InboundMessages.AircraftMessage;
import aisafe.flights.infrastructure.messaging.InboundMessages.RouteMessage;
import aisafe.flights.infrastructure.readmodel.FlightProjector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static aisafe.flights.infrastructure.messaging.MessagingTopology.HEADER_SOURCE_INSTANCE;

@Slf4j
@Component
@ConditionalOnProperty(name = "aisafe.messaging.enabled", havingValue = "true")
public class EventListeners {
    private final FlightProjector projector;
    private final CatalogUpdater catalog;
    private final String instanceId;

    public EventListeners(FlightProjector projector, CatalogUpdater catalog,
                          @Value("${aisafe.messaging.instance-id:flight-operations}") String instanceId) {
        this.projector = projector;
        this.catalog = catalog;
        this.instanceId = instanceId;
    }

    @RabbitListener(queues = "${aisafe.messaging.flight-queue}")
    public void onFlightEvent(Message message) {
        if (instanceId.equals(message.getMessageProperties().getHeader(HEADER_SOURCE_INSTANCE))) {
            return;
        }
        FlightEvent event = parse(message, FlightEvent.class);
        boolean changed = projector.apply(event);
        log.info("Projected {} for flight {} from another replica (changed: {})", event.eventType(), event.flightId(), changed);
    }

    @RabbitListener(queues = "${aisafe.messaging.aircraft-queue}")
    public void onAircraftEvent(Message message) {
        AircraftMessage aircraft = parse(message, AircraftMessage.class);
        if (aircraft.registrationNumber() == null || aircraft.effectiveStatus() == null) {
            throw new AmqpRejectAndDontRequeueException("Aircraft event without registration or status");
        }
        catalog.aircraftChanged(aircraft.registrationNumber(), aircraft.modelName(), aircraft.effectiveStatus(),
                aircraft.seatCapacity(), aircraft.range());
        projector.applyAircraftStatus(aircraft.registrationNumber().toUpperCase(), aircraft.modelName(), aircraft.effectiveStatus());
        log.info("Aircraft {} is now {} (event from Aircraft & Maintenance)", aircraft.registrationNumber(), aircraft.effectiveStatus());
    }

    @RabbitListener(queues = "${aisafe.messaging.route-queue}")
    public void onRouteEvent(Message message) {
        RouteMessage route = parse(message, RouteMessage.class);
        if (route.originIataCode() == null || route.destinationIataCode() == null) {
            throw new AmqpRejectAndDontRequeueException("Route event without origin or destination");
        }
        catalog.routeChanged(route.originIataCode(), route.destinationIataCode(), route.minimumRange(),
                route.minimumCapacity(), route.status(), route.isDeletion());
        log.info("Route {} -> {} {} (event from Airports & Routes)", route.originIataCode(), route.destinationIataCode(),
                route.eventType());
    }

    private static <T> T parse(Message message, Class<T> type) {
        try {
            return EventJson.read(message.getBody(), type);
        } catch (IllegalArgumentException e) {
            throw new AmqpRejectAndDontRequeueException(e.getMessage(), e);
        }
    }
}
