package aisafe.security.infrastructure;

import aisafe.security.application.AuthenticateUserUseCase;
import aisafe.security.application.RegisterUserUseCase;
import aisafe.security.application.dtos.AuthResponse;
import aisafe.security.application.dtos.LoginRequest;
import aisafe.security.application.dtos.RegisterRequest;
import aisafe.security.domain.Role;
import aisafe.security.application.JwtService;
import aisafe.security.domain.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.test.context.support.WithMockUser;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthenticateUserUseCase authenticateUser;

    @MockitoBean
    private RegisterUserUseCase registerUser;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private AuthenticationProvider authenticationProvider;

    @Test
    @WithMockUser(roles = "ADMIN")
    void ensureRegisterReturns201() throws Exception {
        RegisterRequest request = new RegisterRequest("newuser", "password", Role.BACKOFFICE_OPERATOR);
        AuthResponse response = new AuthResponse("token-123");

        when(registerUser.execute(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").value("token-123"));
    }

    @Test
    void ensureRegisterRejectsUnauthenticatedRequests() throws Exception {
        RegisterRequest request = new RegisterRequest("newuser", "password", Role.ADMIN);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "BACKOFFICE_OPERATOR")
    void ensureRegisterRejectsNonAdminUsers() throws Exception {
        RegisterRequest request = new RegisterRequest("newuser", "password", Role.ADMIN);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ensureLoginReturns200() throws Exception {
        LoginRequest request = new LoginRequest("user", "password");
        AuthResponse response = new AuthResponse("token-456");

        when(authenticateUser.execute(any())).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-456"));
    }
}
