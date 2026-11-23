package aisafe.flights.application.ports;

import aisafe.flights.application.events.FlightEvent;

public interface FlightEventPublisher {
    void publish(FlightEvent event);
}
