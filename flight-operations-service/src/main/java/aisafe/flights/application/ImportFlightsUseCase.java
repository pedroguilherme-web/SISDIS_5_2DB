package aisafe.flights.application;

import aisafe.flights.application.dtos.ScheduleFlightRequest;
import aisafe.flights.domain.FlightStatus;
import aisafe.shared.application.UseCase;
import aisafe.shared.application.dtos.BulkImportResult;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@UseCase
@RequiredArgsConstructor
public class ImportFlightsUseCase {
    private final ScheduleFlightUseCase scheduleFlightUseCase;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BulkImportResult<String> execute(InputStream csv) {
        BulkImportResult<String> result = new BulkImportResult<>();

        try (Reader reader = new InputStreamReader(csv, StandardCharsets.UTF_8);
             CSVReader csvReader = new CSVReader(reader)) {
            String[] header = csvReader.readNext();
            if (header == null) {
                result.addError(0, "File", "CSV file is empty");
                return result;
            }

            int flightNumberIndex = -1, originIndex = -1, destIndex = -1, aircraftIndex = -1, depIndex = -1, arrIndex = -1, statusIndex = -1;
            for (int i = 0; i < header.length; i++) {
                String col = header[i].trim().toLowerCase().replace("﻿", "");
                switch (col) {
                    case "flightnumber" -> flightNumberIndex = i;
                    case "routeorigin" -> originIndex = i;
                    case "routedestination" -> destIndex = i;
                    case "aircraftregistration" -> aircraftIndex = i;
                    case "departuredate" -> depIndex = i;
                    case "arrivaldate" -> arrIndex = i;
                    case "status" -> statusIndex = i;
                    default -> {  }
                }
            }

            if (originIndex == -1 || destIndex == -1 || aircraftIndex == -1 || depIndex == -1 || arrIndex == -1) {
                result.addError(0, "Headers", "Missing required columns (routeOrigin, routeDestination, aircraftRegistration, departureDate, arrivalDate)");
                return result;
            }

            String[] line;
            int rowNumber = 1;

            while ((line = csvReader.readNext()) != null) {
                rowNumber++;
                try {
                    String origin = required(line, originIndex, "routeOrigin");
                    String dest = required(line, destIndex, "routeDestination");
                    String aircraft = required(line, aircraftIndex, "aircraftRegistration");
                    OffsetDateTime departureDate = parseDate(required(line, depIndex, "departureDate"), "departureDate");
                    OffsetDateTime arrivalDate = parseDate(required(line, arrIndex, "arrivalDate"), "arrivalDate");
                    FlightStatus status = parseStatus(getValue(line, statusIndex));
                    String flightNumber = getValue(line, flightNumberIndex);

                    scheduleFlightUseCase.execute(
                            new ScheduleFlightRequest(aircraft, origin, dest, departureDate, arrivalDate), status);

                    result.addSuccess(flightNumber != null ? flightNumber : aircraft + "-" + origin + "-" + dest);
                } catch (Exception ex) {
                    result.addError(rowNumber, String.join(",", line), ex.getMessage());
                }
            }
        } catch (Exception e) {
            result.addError(0, "File Processing", "Failed to parse CSV file: " + e.getMessage());
        }

        return result;
    }

    private static String getValue(String[] line, int index) {
        if (index == -1 || index >= line.length) return null;
        String val = line[index].trim();
        return val.isEmpty() ? null : val;
    }

    private static String required(String[] line, int index, String column) {
        String value = getValue(line, index);
        if (value == null) {
            throw new IllegalArgumentException("Missing value for column '" + column + "'.");
        }
        return value;
    }

    private static OffsetDateTime parseDate(String value, String column) {
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid " + column + " '" + value + "'. Expected ISO-8601 with offset, e.g. 2026-09-01T08:00:00Z.");
        }
    }

    private static FlightStatus parseStatus(String value) {
        if (value == null) {
            return FlightStatus.SCHEDULED;
        }
        try {
            return FlightStatus.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid status '" + value + "'.");
        }
    }
}
