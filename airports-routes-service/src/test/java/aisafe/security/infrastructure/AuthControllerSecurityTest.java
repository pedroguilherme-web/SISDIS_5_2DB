package aisafe.security.infrastructure;

import aisafe.security.application.AuthenticateUserUseCase;
import aisafe.security.application.JwtService;
import aisafe.security.application.RegisterUserUseCase;
import aisafe.security.application.dtos.AuthResponse;
import aisafe.security.application.dtos.LoginRequest;
import aisafe.security.application.dtos.RegisterRequest;
import aisafe.security.domain.Role;
import aisafe.security.domain.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@AutoConfigureMockMvc
class AuthControllerSecurityTest {

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
    void registrationRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "BACKOFFICE_OPERATOR")
    void registrationRequiresAdminRole() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminCanRegisterUsers() throws Exception {
        when(registerUser.execute(any())).thenReturn(new AuthResponse("token"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registrationRequest()))
                .andExpect(status().isCreated());
    }

    @Test
    void loginRemainsPublic() throws Exception {
        when(authenticateUser.execute(any())).thenReturn(new AuthResponse("token"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("user", "password"))))
                .andExpect(status().isOk());
    }

    private String registrationRequest() throws Exception {
        return objectMapper.writeValueAsString(
                new RegisterRequest("new-user", "password", Role.BACKOFFICE_OPERATOR));
    }
}
