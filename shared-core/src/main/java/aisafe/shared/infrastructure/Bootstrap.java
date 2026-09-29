package aisafe.shared.infrastructure;

import aisafe.security.domain.Role;
import aisafe.security.domain.User;
import aisafe.security.domain.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Bootstrap implements ApplicationRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public Bootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() == 0) {
            userRepository.save(new User("admin", passwordEncoder.encode("admin123"), Role.ADMIN));
            userRepository.save(new User("operator", passwordEncoder.encode("operator123"), Role.BACKOFFICE_OPERATOR));
            userRepository.save(new User("atcc", passwordEncoder.encode("atcc123"), Role.ATCC));
            userRepository.save(new User("technician", passwordEncoder.encode("technician123"), Role.MAINTENANCE_TECHNICIAN));
            userRepository.save(new User("supervisor", passwordEncoder.encode("supervisor123"), Role.MAINTENANCE_SUPERVISOR));
        }
    }
}
