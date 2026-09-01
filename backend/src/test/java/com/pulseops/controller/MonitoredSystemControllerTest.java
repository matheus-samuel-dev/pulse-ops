package com.pulseops.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pulseops.config.SecurityConfig;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.system.Environment;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import com.pulseops.dto.system.MonitoredSystemRequest;
import com.pulseops.dto.system.MonitoredSystemResponse;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.security.JwtAuthenticationFilter;
import com.pulseops.security.JwtService;
import com.pulseops.security.PulseOpsPrincipal;
import com.pulseops.security.PulseOpsUserDetailsService;
import com.pulseops.security.RestAccessDeniedHandler;
import com.pulseops.security.RestAuthenticationEntryPoint;
import com.pulseops.security.SecurityErrorWriter;
import com.pulseops.service.MonitoredSystemService;
import com.pulseops.service.monitoring.MonitoringService;
import java.math.BigDecimal;
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

@WebMvcTest(MonitoredSystemController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityErrorWriter.class
})
@ActiveProfiles("test")
@DisplayName("MonitoredSystemController HTTP and RBAC contract")
class MonitoredSystemControllerTest {

    private static final UUID SYSTEM_ID = UUID.fromString("41c972b7-baf8-457b-b87b-afb194d72f78");
    private static final OffsetDateTime TIMESTAMP =
            OffsetDateTime.of(2026, 8, 27, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MonitoredSystemService systemService;

    @MockitoBean
    private MonitoringService monitoringService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private PulseOpsUserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/systems returns 200 and serialized systems to a viewer")
    void shouldListSystemsForViewer() throws Exception {
        PulseOpsPrincipal principal = new PulseOpsPrincipal(
                UUID.fromString("a2851b84-9b1b-41d0-b180-8233e4640a72"),
                "Demo Viewer",
                "viewer@pulseops.dev",
                "encoded-password",
                "VIEWER"
        );
        when(jwtService.extractUsername("valid-viewer-token")).thenReturn(principal.email());
        when(userDetailsService.loadUserByUsername(principal.email())).thenReturn(principal);
        when(jwtService.isValid("valid-viewer-token", principal)).thenReturn(true);
        when(systemService.findAll()).thenReturn(List.of(systemResponse()));

        mockMvc.perform(get("/api/systems")
                        .header("Authorization", "Bearer valid-viewer-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(SYSTEM_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("PlaySpace"))
                .andExpect(jsonPath("$[0].environment").value("PRODUCTION"))
                .andExpect(jsonPath("$[0].status").value("OPERATIONAL"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].targetAvailability").value(99.9));

        verify(jwtService).extractUsername("valid-viewer-token");
        verify(userDetailsService).loadUserByUsername(principal.email());
        verify(jwtService).isValid("valid-viewer-token", principal);
        verify(systemService).findAll();
    }

    @Test
    @DisplayName("GET /api/systems returns security JSON with 401 when unauthenticated")
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/systems"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Autenticação necessária"))
                .andExpect(jsonPath("$.path").value("/api/systems"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(systemService, never()).findAll();
    }

    @Test
    @DisplayName("POST /api/systems returns 201 for an administrator")
    void shouldCreateSystemForAdmin() throws Exception {
        MonitoredSystemRequest request = validRequest();
        when(systemService.create(any(MonitoredSystemRequest.class))).thenReturn(systemResponse());

        mockMvc.perform(post("/api/systems")
                        .with(user("admin@pulseops.dev").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(SYSTEM_ID.toString()))
                .andExpect(jsonPath("$.name").value("PlaySpace"))
                .andExpect(jsonPath("$.baseUrl").value("https://playspace.example.com"))
                .andExpect(jsonPath("$.expectedStatusCode").value(200))
                .andExpect(jsonPath("$.timeoutMs").value(3000));

        verify(systemService).create(eq(request));
    }

    @Test
    @DisplayName("POST /api/systems returns 403 JSON for a developer")
    void shouldForbidCreationForDeveloper() throws Exception {
        mockMvc.perform(post("/api/systems")
                        .with(user("developer@pulseops.dev").roles("DEVELOPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Acesso negado para este recurso"))
                .andExpect(jsonPath("$.path").value("/api/systems"));

        verify(systemService, never()).create(any(MonitoredSystemRequest.class));
    }

    @Test
    @DisplayName("POST /api/systems returns structured 400 validation errors")
    void shouldRejectInvalidSystemPayload() throws Exception {
        MonitoredSystemRequest invalid = new MonitoredSystemRequest(
                "",
                null,
                "ftp://invalid.example.com",
                "",
                null,
                true,
                99,
                0,
                0,
                new BigDecimal("101.000")
        );

        mockMvc.perform(post("/api/systems")
                        .with(user("admin@pulseops.dev").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Um ou mais campos são inválidos"))
                .andExpect(jsonPath("$.path").value("/api/systems"))
                .andExpect(jsonPath("$.validationErrors.name").exists())
                .andExpect(jsonPath("$.validationErrors.baseUrl").value("baseUrl deve usar HTTP ou HTTPS"))
                .andExpect(jsonPath("$.validationErrors.environment").exists())
                .andExpect(jsonPath("$.validationErrors.timeoutMs").exists())
                .andExpect(jsonPath("$.validationErrors.targetAvailability").exists());

        verify(systemService, never()).create(any(MonitoredSystemRequest.class));
    }

    @Test
    @DisplayName("GET /api/systems/{id} translates a missing system to standardized 404")
    void shouldReturnNotFoundForUnknownSystem() throws Exception {
        when(systemService.findById(SYSTEM_ID))
                .thenThrow(new ResourceNotFoundException("Monitored system", SYSTEM_ID));

        mockMvc.perform(get("/api/systems/{id}", SYSTEM_ID)
                        .with(user("viewer@pulseops.dev").roles("VIEWER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message")
                        .value("Monitored system not found: " + SYSTEM_ID))
                .andExpect(jsonPath("$.path").value("/api/systems/" + SYSTEM_ID));
    }

    @Test
    @DisplayName("PUT /api/systems/{id} returns 200 and delegates the validated update for an administrator")
    void shouldUpdateSystemForAdmin() throws Exception {
        MonitoredSystemRequest request = validRequest();
        when(systemService.update(SYSTEM_ID, request)).thenReturn(systemResponse());

        mockMvc.perform(put("/api/systems/{id}", SYSTEM_ID)
                        .with(user("admin@pulseops.dev").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SYSTEM_ID.toString()))
                .andExpect(jsonPath("$.name").value("PlaySpace"))
                .andExpect(jsonPath("$.status").value("OPERATIONAL"))
                .andExpect(jsonPath("$.targetAvailability").value(99.9));

        verify(systemService).update(SYSTEM_ID, request);
    }

    @Test
    @DisplayName("PUT /api/systems/{id} returns 403 for a viewer")
    void shouldForbidUpdateForViewer() throws Exception {
        mockMvc.perform(put("/api/systems/{id}", SYSTEM_ID)
                        .with(user("viewer@pulseops.dev").roles("VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Acesso negado para este recurso"))
                .andExpect(jsonPath("$.path").value("/api/systems/" + SYSTEM_ID));

        verify(systemService, never()).update(eq(SYSTEM_ID), any(MonitoredSystemRequest.class));
    }

    @Test
    @DisplayName("DELETE /api/systems/{id} returns 204 for an administrator")
    void shouldDeleteSystemForAdmin() throws Exception {
        mockMvc.perform(delete("/api/systems/{id}", SYSTEM_ID)
                        .with(user("admin@pulseops.dev").roles("ADMIN")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(systemService).delete(SYSTEM_ID);
    }

    @Test
    @DisplayName("DELETE /api/systems/{id} returns 403 for a developer")
    void shouldForbidDeleteForDeveloper() throws Exception {
        mockMvc.perform(delete("/api/systems/{id}", SYSTEM_ID)
                        .with(user("developer@pulseops.dev").roles("DEVELOPER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.path").value("/api/systems/" + SYSTEM_ID));

        verify(systemService, never()).delete(SYSTEM_ID);
    }

    @Test
    @DisplayName("POST /api/systems/{id}/checks returns 201 for a developer")
    void shouldRunHealthCheckForDeveloper() throws Exception {
        HealthCheck healthCheck = successfulHealthCheck();
        when(monitoringService.checkSystem(SYSTEM_ID)).thenReturn(healthCheck);

        mockMvc.perform(post("/api/systems/{id}/checks", SYSTEM_ID)
                        .with(user("developer@pulseops.dev").roles("DEVELOPER")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(healthCheck.getId().toString()))
                .andExpect(jsonPath("$.systemId").value(SYSTEM_ID.toString()))
                .andExpect(jsonPath("$.checkedAt").value("2026-08-27T12:00:00Z"))
                .andExpect(jsonPath("$.httpStatus").value(200))
                .andExpect(jsonPath("$.responseTimeMs").value(142))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.errorMessage").doesNotExist());

        verify(monitoringService).checkSystem(SYSTEM_ID);
    }

    @Test
    @DisplayName("POST /api/systems/{id}/checks returns 403 for a viewer")
    void shouldForbidHealthCheckForViewer() throws Exception {
        mockMvc.perform(post("/api/systems/{id}/checks", SYSTEM_ID)
                        .with(user("viewer@pulseops.dev").roles("VIEWER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Acesso negado para este recurso"));

        verify(monitoringService, never()).checkSystem(SYSTEM_ID);
    }

    @Test
    @DisplayName("GET /api/systems ignores an invalid bearer token and returns standardized 401")
    void shouldRejectInvalidBearerToken() throws Exception {
        when(jwtService.extractUsername("expired-token"))
                .thenThrow(new IllegalArgumentException("Expired JWT"));

        mockMvc.perform(get("/api/systems")
                        .header("Authorization", "Bearer expired-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Autenticação necessária"))
                .andExpect(jsonPath("$.path").value("/api/systems"));

        verify(jwtService).extractUsername("expired-token");
        verify(systemService, never()).findAll();
    }

    private MonitoredSystemRequest validRequest() {
        return new MonitoredSystemRequest(
                "PlaySpace",
                "Plataforma de reservas esportivas",
                "https://playspace.example.com",
                "/actuator/health",
                Environment.PRODUCTION,
                true,
                200,
                3000,
                500,
                new BigDecimal("99.900")
        );
    }

    private MonitoredSystemResponse systemResponse() {
        return new MonitoredSystemResponse(
                SYSTEM_ID,
                "PlaySpace",
                "Plataforma de reservas esportivas",
                "https://playspace.example.com",
                "/actuator/health",
                Environment.PRODUCTION,
                SystemStatus.OPERATIONAL,
                true,
                200,
                3000,
                500,
                new BigDecimal("99.900"),
                TIMESTAMP,
                TIMESTAMP
        );
    }

    private HealthCheck successfulHealthCheck() {
        MonitoredSystem system = new MonitoredSystem();
        system.setId(SYSTEM_ID);
        system.setName("PlaySpace");
        system.setBaseUrl("https://playspace.example.com");
        system.setHealthEndpoint("/actuator/health");
        system.setEnvironment(Environment.PRODUCTION);
        system.setStatus(SystemStatus.OPERATIONAL);

        HealthCheck healthCheck = new HealthCheck();
        healthCheck.setId(UUID.fromString("8124f03a-260d-4cf0-a489-a734ec070468"));
        healthCheck.setMonitoredSystem(system);
        healthCheck.setCheckedAt(TIMESTAMP);
        healthCheck.setHttpStatus(200);
        healthCheck.setResponseTimeMs(142);
        healthCheck.setSuccess(true);
        return healthCheck;
    }
}
