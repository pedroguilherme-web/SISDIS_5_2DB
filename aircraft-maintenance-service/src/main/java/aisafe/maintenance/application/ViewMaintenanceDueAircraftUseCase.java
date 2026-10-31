package aisafe.maintenance.application;

import aisafe.aircrafts.domain.Aircraft;
import aisafe.aircrafts.domain.AircraftRepository;
import aisafe.aircrafts.domain.FlightServiceClient;
import aisafe.aircrafts.domain.ModelName;
import aisafe.maintenance.application.dtos.MaintenanceDueAircraftResponse;
import aisafe.maintenance.domain.MaintenanceRecord;
import aisafe.maintenance.domain.MaintenanceRecordRepository;
import aisafe.maintenance.domain.MaintenanceTemplate;
import aisafe.maintenance.domain.MaintenanceTemplateRepository;
import aisafe.shared.application.UseCase;
import aisafe.shared.domain.PaginatedResult;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@UseCase(readOnly = true)
public class ViewMaintenanceDueAircraftUseCase {

    private final AircraftRepository aircraftRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final MaintenanceTemplateRepository maintenanceTemplateRepository;
    private final FlightServiceClient flightServiceClient;

    private final Double defaultFlightHoursThreshold;
    private final Long defaultDaysThreshold;

    public ViewMaintenanceDueAircraftUseCase(
            AircraftRepository aircraftRepository,
            MaintenanceRecordRepository maintenanceRecordRepository,
            MaintenanceTemplateRepository maintenanceTemplateRepository,
            FlightServiceClient flightServiceClient,
            @Value("${aisafe.maintenance.threshold.default-flight-hours:300}") Double defaultFlightHoursThreshold,
            @Value("${aisafe.maintenance.threshold.default-days:365}") Long defaultDaysThreshold) {
        this.aircraftRepository = aircraftRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.maintenanceTemplateRepository = maintenanceTemplateRepository;
        this.flightServiceClient = flightServiceClient;
        this.defaultFlightHoursThreshold = defaultFlightHoursThreshold;
        this.defaultDaysThreshold = defaultDaysThreshold;
    }

    public PaginatedResult<MaintenanceDueAircraftResponse> execute(int pageNumber, int pageSize) {
        List<MaintenanceDueAircraftResponse> dueList = new ArrayList<>();
        List<Aircraft> allAircraft = aircraftRepository.findAll();
        List<MaintenanceTemplate> allTemplates = maintenanceTemplateRepository.findAll();
        LocalDateTime now = LocalDateTime.now();

        for (Aircraft aircraft : allAircraft) {
            String modelName = aircraft.getModel().getModelName();

            // Find templates applicable to this model
            List<MaintenanceTemplate> applicableTemplates = allTemplates.stream()
                    .filter(t -> t.getApplicableModelNames().contains(new ModelName(modelName)))
                    .toList();

            List<MaintenanceRecord> completedRecords = maintenanceRecordRepository.findCompletedByAircraft(aircraft.getRegistrationNumber());

            if (!applicableTemplates.isEmpty()) {
                for (MaintenanceTemplate template : applicableTemplates) {
                    MaintenanceRecord lastCompleted = completedRecords.stream()
                            .filter(r -> r.getTemplate().getName().equals(template.getName()))
                            .findFirst() // completedRecords is sorted by completedAt desc
                            .orElse(null);

                    LocalDateTime startCalDate;

                    if (lastCompleted != null && lastCompleted.getCompletedAt() != null) {
                        startCalDate = lastCompleted.getCompletedAt();
                    } else {
                        startCalDate = aircraft.getManufacturingDate().atStartOfDay();
                    }

                    long elapsedDays = Duration.between(startCalDate, now).toDays();
                    Double flightHours = flightServiceClient.calculateOperationalHoursSince(aircraft.getRegistrationNumber(), startCalDate);
                    if (flightHours == null) {
                        flightHours = 0.0;
                    }

                    boolean due = false;
                    String reason = "";
                    if (flightHours >= template.getIntervalFlightHours()) {
                        due = true;
                        reason += String.format("Exceeded flight hours limit (%.1f/%.1f hrs). ", flightHours, (double) template.getIntervalFlightHours());
                    }
                    if (elapsedDays >= template.getIntervalDays()) {
                        due = true;
                        reason += String.format("Exceeded elapsed days limit (%d/%d days). ", elapsedDays, template.getIntervalDays());
                    }

                    if (due) {
                        dueList.add(new MaintenanceDueAircraftResponse(
                                aircraft.getRegistrationNumber().getNumber(),
                                modelName,
                                reason.trim(),
                                flightHours,
                                elapsedDays,
                                template.getName()
                        ));
                    }
                }
            } else {
                // Fallback to global thresholds if no templates apply
                LocalDateTime startCalDate;

                MaintenanceRecord lastCompleted = completedRecords.isEmpty() ? null : completedRecords.get(0);
                if (lastCompleted != null && lastCompleted.getCompletedAt() != null) {
                    startCalDate = lastCompleted.getCompletedAt();
                } else {
                    startCalDate = aircraft.getManufacturingDate().atStartOfDay();
                }

                long elapsedDays = Duration.between(startCalDate, now).toDays();
                Double flightHours = flightServiceClient.calculateOperationalHoursSince(aircraft.getRegistrationNumber(), startCalDate);
                if (flightHours == null) {
                    flightHours = 0.0;
                }

                boolean due = false;
                String reason = "";
                if (flightHours >= defaultFlightHoursThreshold) {
                    due = true;
                    reason += String.format("Exceeded default flight hours limit (%.1f/%.1f hrs). ", flightHours, defaultFlightHoursThreshold);
                }
                if (elapsedDays >= defaultDaysThreshold) {
                    due = true;
                    reason += String.format("Exceeded default elapsed days limit (%d/%d days). ", elapsedDays, defaultDaysThreshold);
                }

                if (due) {
                    dueList.add(new MaintenanceDueAircraftResponse(
                            aircraft.getRegistrationNumber().getNumber(),
                            modelName,
                            reason.trim(),
                            flightHours,
                            elapsedDays,
                            "DEFAULT_THRESHOLD"
                    ));
                }
            }
        }

        int total = dueList.size();
        int fromIndex = Math.min(pageNumber * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);

        List<MaintenanceDueAircraftResponse> paged = dueList.subList(fromIndex, toIndex);
        return new PaginatedResult<>(paged, total);
    }
}
