package com.pulseops.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseops.config.SecurityConfig;
import com.pulseops.domain.user.UserRole;
import com.pulseops.dto.auth.AuthResponse;
import com.pulseops.dto.auth.LoginRequest;
import com.pulseops.dto.auth.RegisterRequest;
import com.pulseops.dto.user.UserResponse;
import com.pulseops.exception.ConflictException;
import com.pulseops.security.JwtAuthenticationFilter;
import com.pulseops.security.JwtService;
import com.pulseops.security.PulseOpsUserDetailsService;
import com.pulseops.security.RestAccessDeniedHandler;
import com.pulseops.security.RestAuthenticationEntryPoint;
import com.pulseops.security.SecurityErrorWriter;
import com.pulseops.service.AuthService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityErrorWriter.class
})
@ActiveProfiles("test")
@DisplayName("AuthController HTTP contract")
class AuthControllerTest {

    private static final UUID USER_ID = UUID.fromString("a2851b84-9b1b-41d0-b180-8233e4640a72");
    private static final Instant EXPIRATION = Instant.parse("2026-08-27T13:00:00Z");
    private static final OffsetDateTime CREATED_AT =
            OffsetDateTime.of(2026, 8, 27, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private PulseOpsUserDetailsService userDetailsService;

    @Test
    @DisplayName("POST /api/auth/login returns 200 and the JWT contract")
    void shouldLoginAndReturnJwtContract() throws Exception {
        AuthResponse response = authResponse("developer@pulseops.dev", UserRole.DEVELOPER);
        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("developer@pulseops.dev", "StrongPass123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt.integration.token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").value("2026-08-27T13:00:00Z"))
                .andExpect(jsonPath("$.user.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.user.email").value("developer@pulseops.dev"))
                .andExpect(jsonPath("$.user.role").value("DEVELOPER"));

        ArgumentCaptor<LoginRequest> requestCaptor = ArgumentCaptor.forClass(LoginRequest.class);
        verify(authService).login(requestCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(requestCaptor.getValue().password()).isEqualTo("StrongPass123");
    }

    @Test
    @DisplayName("POST /api/auth/register returns 201 for a valid new user")
    void shouldRegisterAndReturnCreated() throws Exception {
        AuthResponse response = authResponse("viewer@pulseops.dev", UserRole.VIEWER);
        when(authService.register(any(RegisterRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Demo Viewer", "viewer@pulseops.dev", "StrongPass123"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.name").value("PulseOps User"))
                .andExpect(jsonPath("$.user.email").value("viewer@pulseops.dev"))
                .andExpect(jsonPath("$.user.role").value("VIEWER"));

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/login returns standardized 400 validation details")
    void shouldRejectInvalidLoginPayload() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"invalid-email\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Um ou mais campos são inválidos"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.validationErrors.email").value("E-mail deve ser válido"))
                .andExpect(jsonPath("$.validationErrors.password").value("Senha é obrigatória"));

        verify(authService, never()).login(any(LoginRequest.class));
    }

    @Test
    @DisplayName("POST /api/auth/login translates bad credentials to standardized 401")
    void shouldReturnUnauthorizedForBadCredentials() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("developer@pulseops.dev", "WrongPass123"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("E-mail ou senha inválidos"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    @Test
    @DisplayName("POST /api/auth/register translates duplicate email to 409")
    void shouldReturnConflictForDuplicateRegistration() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ConflictException("E-mail já cadastrado"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("Duplicate", "duplicate@pulseops.dev", "StrongPass123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("E-mail já cadastrado"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    @DisplayName("POST /api/auth/register rejects invalid account data before calling the service")
    void shouldRejectInvalidRegistrationPayload() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new RegisterRequest("", "not-an-email", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Um ou mais campos são inválidos"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"))
                .andExpect(jsonPath("$.validationErrors.name").value("Nome é obrigatório"))
                .andExpect(jsonPath("$.validationErrors.email").value("E-mail deve ser válido"))
                .andExpect(jsonPath("$.validationErrors.password")
                        .value("Senha deve ter entre 8 e 72 caracteres"));

        verify(authService, never()).register(any(RegisterRequest.class));
    }

    private AuthResponse authResponse(String email, UserRole role) {
        UserResponse user = new UserResponse(
                USER_ID,
                "PulseOps User",
                email,
                role,
                CREATED_AT,
                CREATED_AT
        );
        return new AuthResponse("jwt.integration.token", "Bearer", EXPIRATION, user);
    }
}
