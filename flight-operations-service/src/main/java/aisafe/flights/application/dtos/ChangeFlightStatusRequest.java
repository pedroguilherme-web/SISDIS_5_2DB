package aisafe.flights.application.dtos;

import aisafe.flights.domain.FlightStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeFlightStatusRequest(@NotNull FlightStatus status) {}
