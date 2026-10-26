package aisafe.shared.infrastructure;

import aisafe.security.domain.Role;
import aisafe.security.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BootstrapSecurityTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ApplicationArguments args = mock(ApplicationArguments.class);

    @Test
    void createsConfiguredAdminWithoutDefaultCredentials() {
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("user-supplied-secret")).thenReturn("encoded-secret");

        new Bootstrap(userRepository, passwordEncoder, "initial-admin", "user-supplied-secret").run(args);

        verify(userRepository).save(argThat(user ->
                user.getUsername().equals("initial-admin")
                        && user.getPassword().equals("encoded-secret")
                        && user.getRole() == Role.ADMIN));
    }

    @Test
    void failsClearlyWhenBootstrapCredentialsAreNotConfigured() {
        when(userRepository.count()).thenReturn(0L);

        assertThrows(IllegalStateException.class,
                () -> new Bootstrap(userRepository, passwordEncoder, "", "").run(args));

        verify(userRepository, never()).save(any());
    }
}
