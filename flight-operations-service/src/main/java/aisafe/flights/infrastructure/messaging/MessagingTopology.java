package aisafe.flights.infrastructure.messaging;

public final class MessagingTopology {
    public static final String BUS_EXCHANGE = "aisafe.exchange";
    public static final String ROUTES_EXCHANGE = "aisafe.routes.events";

    public static final String FLIGHT_SCHEDULED = "flight.scheduled";
    public static final String FLIGHT_STATUS_CHANGED = "flight.status.changed";

    public static final String FLIGHT_COMPLETED = "flight.completed";

    public static final String AIRCRAFT_REGISTERED = "aircraft.registered";
    public static final String AIRCRAFT_STATUS_CHANGED = "aircraft.status.changed";
    public static final String ROUTE_CHANGED_PATTERN = "routes.#";

    public static final String HEADER_SOURCE_INSTANCE = "sourceInstance";

    private MessagingTopology() {
    }
}
