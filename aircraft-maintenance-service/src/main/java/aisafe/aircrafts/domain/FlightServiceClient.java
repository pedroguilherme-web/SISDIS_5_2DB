package aisafe.aircrafts.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Domain interface for interacting with the external Flight Operations Service.
 * Decouples the Aircraft & Maintenance domain from the Flights domain.
 */
public interface FlightServiceClient {

    Double calculateTotalOperationalHoursByRegistration(RegistrationNumber registrationNumber);

    Double calculateOperationalHoursSince(RegistrationNumber registrationNumber, LocalDateTime since);

    boolean existsByAircraftRegistration(RegistrationNumber registrationNumber);

    List<TopModelFlightData> findTopModelsByFlightHours(int limit);

    List<TopModelFlightData> findTopModelsByAssignments(int limit);

    List<FlightTimelineData> findFlightTimelineByRegistration(RegistrationNumber registrationNumber);
}
