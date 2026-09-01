package com.pulseops.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseops.config.SecurityConfig;
import com.pulseops.domain.user.UserRole;
import com.pulseops.dto.user.CreateUserRequest;
import com.pulseops.dto.user.UserResponse;
import com.pulseops.security.JwtAuthenticationFilter;
import com.pulseops.security.JwtService;
import com.pulseops.security.PulseOpsUserDetailsService;
import com.pulseops.security.RestAccessDeniedHandler;
import com.pulseops.security.RestAuthenticationEntryPoint;
import com.pulseops.security.SecurityErrorWriter;
import com.pulseops.service.UserService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityErrorWriter.class
})
@ActiveProfiles("test")
@DisplayName("UserController administrator RBAC contract")
class UserControllerSecurityTest {

    private static final UUID USER_ID = UUID.fromString("a2851b84-9b1b-41d0-b180-8233e4640a72");
    private static final OffsetDateTime TIMESTAMP =
            OffsetDateTime.of(2026, 8, 27, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private PulseOpsUserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/users returns 200 and user data to an administrator")
    void shouldListUsersForAdmin() throws Exception {
        when(userService.findAll()).thenReturn(List.of(userResponse()));

        mockMvc.perform(get("/api/users")
                        .with(user("admin@pulseops.dev").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(USER_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("Demo Developer"))
                .andExpect(jsonPath("$[0].email").value("developer@pulseops.dev"))
                .andExpect(jsonPath("$[0].role").value("DEVELOPER"));

        verify(userService).findAll();
    }

    @Test
    @DisplayName("GET /api/users returns 403 to a viewer despite generic read access")
    void shouldForbidUserListingForViewer() throws Exception {
        mockMvc.perform(get("/api/users")
                        .with(user("viewer@pulseops.dev").roles("VIEWER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Acesso negado para este recurso"))
                .andExpect(jsonPath("$.path").value("/api/users"));

        verify(userService, never()).findAll();
    }

    @Test
    @DisplayName("GET /api/users returns 401 when unauthenticated")
    void shouldRequireAuthenticationForUserAdministration() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Autenticação necessária"))
                .andExpect(jsonPath("$.path").value("/api/users"));

        verify(userService, never()).findAll();
    }

    @Test
    @DisplayName("POST /api/users returns 403 to a developer and never invokes creation")
    void shouldForbidUserCreationForDeveloper() throws Exception {
        CreateUserRequest request = new CreateUserRequest(
                "Another Developer",
                "another.developer@pulseops.dev",
                "StrongPass123",
                UserRole.DEVELOPER
        );

        mockMvc.perform(post("/api/users")
                        .with(user("developer@pulseops.dev").roles("DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/api/users"));

        verify(userService, never()).create(any(CreateUserRequest.class));
    }

    private UserResponse userResponse() {
        return new UserResponse(
                USER_ID,
                "Demo Developer",
                "developer@pulseops.dev",
                UserRole.DEVELOPER,
                TIMESTAMP,
                TIMESTAMP
        );
    }
}
