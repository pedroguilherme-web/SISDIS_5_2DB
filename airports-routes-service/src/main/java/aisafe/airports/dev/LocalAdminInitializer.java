
package aisafe.airports.dev;

import aisafe.security.domain.Role;
import aisafe.security.domain.User;
import aisafe.security.domain.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.dev.admin.enabled",
        havingValue = "true"
)
public class LocalAdminInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.dev.admin.username}")
    private String username;

    @Value("${app.dev.admin.password}")
    private String password;

    public LocalAdminInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.findByUsername(username).isPresent()) {
            return;
        }

        User admin = new User(
                username,
                passwordEncoder.encode(password),
                Role.ADMIN
        );

        userRepository.save(admin);
        System.out.println("Administrador local de teste criado.");
    }
}
