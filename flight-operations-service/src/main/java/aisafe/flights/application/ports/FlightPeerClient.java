package aisafe.flights.application.ports;

import aisafe.flights.application.dtos.FlightResponse;
import aisafe.flights.application.dtos.FlightTimelineResponse;
import aisafe.flights.application.dtos.FlightUtilizationResponse;
import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.flights.application.dtos.TopModelResponse;
import aisafe.flights.domain.FlightStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface FlightPeerClient {
    List<FlightResponse> findFlightsByAircraft(String registration);

    boolean anyPeerHasOverlappingFlight(String registration, OffsetDateTime departure, OffsetDateTime arrival);

    Optional<FlightResponse> changeStatus(String flightId, FlightStatus newStatus);

    FlightResponse scheduleOn(String ownerUrl, ScheduleFlightRequest request, FlightStatus initialStatus);

    double sumTotalHours(String registration);

    double sumHoursSince(String registration, OffsetDateTime since);

    boolean anyPeerHasFlights(String registration);

    long sumCountByRoute(String origin, String destination);

    List<TopModelResponse> modelUtilizationByHours();

    List<TopModelResponse> modelUtilizationByAssignments();

    List<FlightTimelineResponse> timeline(String registration);

    List<FlightUtilizationResponse> routeUtilization(OffsetDateTime startDate, OffsetDateTime endDate);
}
