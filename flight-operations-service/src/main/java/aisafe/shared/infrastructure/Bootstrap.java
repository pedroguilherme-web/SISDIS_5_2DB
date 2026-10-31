package aisafe.shared.infrastructure;

import aisafe.security.domain.Role;
import aisafe.security.domain.User;
import aisafe.security.domain.UserRepository;
import aisafe.flights.domain.FlightStatus;
import aisafe.flights.domain.ScheduledFlight;
import aisafe.flights.domain.ScheduledFlightRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Component
public class Bootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final ScheduledFlightRepository scheduledFlightRepository;
    private final PasswordEncoder passwordEncoder;

    public Bootstrap(UserRepository userRepository,
                     ScheduledFlightRepository scheduledFlightRepository,
                     PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.scheduledFlightRepository = scheduledFlightRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // Inicialização de Utilizadores
        if (userRepository.count() == 0) {
            userRepository.save(new User("admin", passwordEncoder.encode("admin123"), Role.ADMIN));
            userRepository.save(new User("operator", passwordEncoder.encode("operator123"), Role.BACKOFFICE_OPERATOR));
            userRepository.save(new User("atcc", passwordEncoder.encode("atcc123"), Role.ATCC));
            userRepository.save(new User("technician", passwordEncoder.encode("technician123"), Role.MAINTENANCE_TECHNICIAN));
            userRepository.save(new User("supervisor", passwordEncoder.encode("supervisor123"), Role.MAINTENANCE_SUPERVISOR));
        }

        // Inicialização de Voos Agendados (Usando Strings para Matrículas e Aeroportos)
        if (scheduledFlightRepository.count() == 0) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

            scheduledFlightRepository.save(new ScheduledFlight(
                    now.minusDays(2), now.minusDays(2).plusHours(2),
                    FlightStatus.COMPLETED, "LIS", "OPO", "CS-TKA"));

            scheduledFlightRepository.save(new ScheduledFlight(
                    now.minusDays(1), now.minusDays(1).plusHours(3),
                    FlightStatus.COMPLETED, "LIS", "MAD", "CS-TKA"));

            scheduledFlightRepository.save(new ScheduledFlight(
                    now.minusDays(3), now.minusDays(3).plusHours(4),
                    FlightStatus.COMPLETED, "LIS", "OPO", "CS-TKB"));
        }
    }
}