package aisafe.shared.infrastructure;

import aisafe.aircrafts.domain.*;
import aisafe.maintenance.domain.*;
import aisafe.security.domain.Role;
import aisafe.security.domain.User;
import aisafe.security.domain.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Bootstrap component that populates initial data for users, aircraft models,
 * aircraft fleet instances, maintenance templates, parts, and records.
 */
@Component
public class Bootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final AircraftModelRepository aircraftModelRepository;
    private final AircraftRepository aircraftRepository;
    private final MaintenancePartRepository maintenancePartRepository;
    private final MaintenanceTemplateRepository maintenanceTemplateRepository;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final PasswordEncoder passwordEncoder;

    public Bootstrap(UserRepository userRepository,
                     AircraftModelRepository aircraftModelRepository,
                     AircraftRepository aircraftRepository,
                     MaintenancePartRepository maintenancePartRepository,
                     MaintenanceTemplateRepository maintenanceTemplateRepository,
                     MaintenanceRecordRepository maintenanceRecordRepository,
                     PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.aircraftModelRepository = aircraftModelRepository;
        this.aircraftRepository = aircraftRepository;
        this.maintenancePartRepository = maintenancePartRepository;
        this.maintenanceTemplateRepository = maintenanceTemplateRepository;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Bootstrap security users
        if (userRepository.count() == 0) {
            userRepository.save(new User("admin", passwordEncoder.encode("admin123"), Role.ADMIN));
            userRepository.save(new User("operator", passwordEncoder.encode("operator123"), Role.BACKOFFICE_OPERATOR));
            userRepository.save(new User("atcc", passwordEncoder.encode("atcc123"), Role.ATCC));
            userRepository.save(new User("technician", passwordEncoder.encode("technician123"), Role.MAINTENANCE_TECHNICIAN));
            userRepository.save(new User("supervisor", passwordEncoder.encode("supervisor123"), Role.MAINTENANCE_SUPERVISOR));
        }

        // Bootstrap aircraft models
        if (aircraftModelRepository.count() == 0) {
            aircraftModelRepository.save(new AircraftModel("Airbus A320neo", Manufacturer.AIRBUS, 24210.0, 6570.0,
                    828.0, null, 240));
            aircraftModelRepository.save(new AircraftModel("Boeing 737 MAX", Manufacturer.BOEING, 25941.0, 6570.0,
                    839.0, null, 300));
            aircraftModelRepository.save(new AircraftModel("Embraer E195-E2", Manufacturer.EMBRAER, 13000.0, 4260.0,
                    829.0, null, 146));
        }

        // Bootstrap aircraft fleet
        if (aircraftRepository.count() == 0) {
            AircraftModel a320neo = aircraftModelRepository.findByModelName("Airbus A320neo").orElseThrow();
            AircraftModel b737max = aircraftModelRepository.findByModelName("Boeing 737 MAX").orElseThrow();
            AircraftModel e195e2 = aircraftModelRepository.findByModelName("Embraer E195-E2").orElseThrow();

            aircraftRepository.save(new Aircraft(AircraftStatus.AVAILABLE, LocalDate.parse("2019-05-25"), a320neo,
                    new RegistrationNumber("CS-TKA"), 180, a320neo.getMaxRange(), List.of("WiFi", "In-flight entertainment")));
            aircraftRepository.save(new Aircraft(AircraftStatus.UNDER_MAINTENANCE, LocalDate.parse("2020-08-15"),
                    b737max, new RegistrationNumber("CS-TKB"), 200, b737max.getMaxRange(),
                    List.of("WiFi", "In-flight entertainment", "Extra legroom")));
            aircraftRepository.save(new Aircraft(AircraftStatus.IN_FLIGHT, LocalDate.parse("2018-03-10"), e195e2,
                    new RegistrationNumber("CS-TKC"), 120, e195e2.getMaxRange(), List.of("WiFi")));
        }

        // Bootstrap maintenance templates
        if (maintenanceTemplateRepository.count() == 0) {
            maintenanceTemplateRepository.save(new MaintenanceTemplate(
                    "Inspection to the starter motor",
                    MaintenanceType.INSPECTION,
                    List.of(new ModelName("Airbus A320neo"), new ModelName("Boeing 737 MAX")),
                    List.of("starterMotor inspection"),
                    300,
                    365));
        }

        // Bootstrap maintenance parts
        if (maintenancePartRepository.count() == 0) {
            maintenancePartRepository.save(new MaintenancePart("ST-1001", "Starter Motor",
                    "Motor responsible for starting the aircraft's engines.", 300, 15, MaintenanceComponent.ENGINE));
        }

        // Bootstrap maintenance records
        if (maintenanceRecordRepository.count() == 0) {
            MaintenancePart part = maintenancePartRepository.findByPartNumber("ST-1001").orElseThrow();
            MaintenanceTemplate template = maintenanceTemplateRepository.findByName("Inspection to the starter motor").orElseThrow();
            Aircraft aircraft1 = aircraftRepository.findByRegistrationNumber(new RegistrationNumber("CS-TKA")).orElseThrow();
            Aircraft aircraft2 = aircraftRepository.findByRegistrationNumber(new RegistrationNumber("CS-TKB")).orElseThrow();

            maintenanceRecordRepository.save(new MaintenanceRecord(
                    UUID.randomUUID(), "Simple inspection to the starter motor",
                    LocalDateTime.parse("2024-06-01T10:00:00"), 120, List.of(part),
                    "No issues found during the inspection.", template, MaintenanceStatus.PLANNED,
                    Set.of(MaintenanceComponent.ENGINE), aircraft1.getRegistrationNumber(),
                    java.math.BigDecimal.valueOf(1500.00), null));

            maintenanceRecordRepository.save(new MaintenanceRecord(
                    UUID.randomUUID(), "Detailed inspection to the starter motor",
                    LocalDateTime.parse("2024-06-10T14:00:00"), 240, List.of(part),
                    "Minor wear detected, replacement recommended within the next 6 months.", template,
                    MaintenanceStatus.PLANNED, Set.of(MaintenanceComponent.ENGINE), aircraft2.getRegistrationNumber(),
                    java.math.BigDecimal.valueOf(2750.00), null));

            maintenanceRecordRepository.save(new MaintenanceRecord(
                    UUID.randomUUID(), "Completed starter motor overhaul",
                    LocalDateTime.parse("2024-07-01T08:00:00"), 32, List.of(part),
                    "Overhaul completed successfully.", template, MaintenanceStatus.COMPLETED,
                    Set.of(MaintenanceComponent.ENGINE), aircraft1.getRegistrationNumber(),
                    java.math.BigDecimal.valueOf(3200.00), LocalDateTime.parse("2024-07-02T16:00:00")));
        }
    }
}
