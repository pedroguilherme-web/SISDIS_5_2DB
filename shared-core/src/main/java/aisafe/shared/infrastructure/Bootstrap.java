package aisafe.shared.infrastructure;

import aisafe.security.domain.Role;
import aisafe.security.domain.User;
import aisafe.security.domain.UserRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class Bootstrap implements ApplicationRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;

    public Bootstrap(UserRepository userRepository,
                     PasswordEncoder passwordEncoder,
                     @Value("${aisafe.bootstrap.admin.username:}") String adminUsername,
                     @Value("${aisafe.bootstrap.admin.password:}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() != 0) {
            return;
        }

        if (adminUsername.isBlank() || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "Configure aisafe.bootstrap.admin.username and aisafe.bootstrap.admin.password before first startup");
        }

        userRepository.save(new User(adminUsername, passwordEncoder.encode(adminPassword), Role.ADMIN));
    }
}
