package aisafe.shared.infrastructure;

import aisafe.security.domain.Role;
import aisafe.security.domain.User;
import aisafe.security.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BootstrapTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final ApplicationArguments args = mock(ApplicationArguments.class);

    @Test
    void createsOnlyConfiguredAdminWhenNoUsersExist() {
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("strong-password")).thenReturn("encoded-password");

        new Bootstrap(userRepository, passwordEncoder, "initial-admin", "strong-password").run(args);

        verify(userRepository).save(argThat(user ->
                user.getUsername().equals("initial-admin")
                        && user.getPassword().equals("encoded-password")
                        && user.getRole() == Role.ADMIN));
    }

    @Test
    void doesNotChangeExistingUsers() {
        when(userRepository.count()).thenReturn(1L);

        new Bootstrap(userRepository, passwordEncoder, "initial-admin", "strong-password").run(args);

        verify(userRepository, never()).save(any());
    }

    @Test
    void failsStartupWhenInitialAdminCredentialsAreMissing() {
        when(userRepository.count()).thenReturn(0L);

        assertThrows(IllegalStateException.class,
                () -> new Bootstrap(userRepository, passwordEncoder, "", "").run(args));

        verify(userRepository, never()).save(any());
    }
}
