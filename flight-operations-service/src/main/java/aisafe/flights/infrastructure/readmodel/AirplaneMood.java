package aisafe.flights.infrastructure.readmodel;

import aisafe.flights.domain.FlightStatus;

public record AirplaneMood(String mood, String reason, int sparkleLevel) {
    static final double PROUD_HOURS = 1000.0;

    public static AirplaneMood of(String aircraftStatus, FlightStatus latestFlightStatus,
                                  long completedFlights, double completedHours) {
        if ("UNDER_MAINTENANCE".equalsIgnoreCase(aircraftStatus)) {
            return new AirplaneMood("UNDER_CARE", "Resting in the hangar while the technicians look after it.", 45);
        }
        if ("INACTIVE".equalsIgnoreCase(aircraftStatus)) {
            return new AirplaneMood("GRUMPY", "Grounded and inactive.", 15);
        }
        if (latestFlightStatus == FlightStatus.IN_FLIGHT) {
            return new AirplaneMood("SOARING", "Flying right now.", 100);
        }
        if (latestFlightStatus == FlightStatus.DELAYED) {
            return new AirplaneMood("IMPATIENT", "Its next flight is delayed.", 35);
        }
        if (latestFlightStatus == FlightStatus.CANCELED) {
            return new AirplaneMood("DISAPPOINTED", "Its last flight was canceled.", 25);
        }
        if (completedHours >= PROUD_HOURS) {
            return new AirplaneMood("PROUD", "More than " + (int) PROUD_HOURS + " flight hours completed.", 88);
        }
        if (completedFlights > 0) {
            return new AirplaneMood("HAPPY", completedFlights + " flight(s) completed safely.", 90);
        }
        return new AirplaneMood("EXCITED", "Waiting for its first flight.", 75);
    }
}
