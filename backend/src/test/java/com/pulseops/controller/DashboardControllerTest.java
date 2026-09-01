package com.pulseops.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pulseops.config.SecurityConfig;
import com.pulseops.domain.system.Environment;
import com.pulseops.domain.system.SystemStatus;
import com.pulseops.dto.dashboard.DashboardSummaryResponse;
import com.pulseops.dto.dashboard.ErrorBreakdownResponse;
import com.pulseops.dto.dashboard.LatencyPointResponse;
import com.pulseops.dto.dashboard.SystemHealthResponse;
import com.pulseops.security.JwtAuthenticationFilter;
import com.pulseops.security.JwtService;
import com.pulseops.security.PulseOpsUserDetailsService;
import com.pulseops.security.RestAccessDeniedHandler;
import com.pulseops.security.RestAuthenticationEntryPoint;
import com.pulseops.security.SecurityErrorWriter;
import com.pulseops.service.DashboardService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityErrorWriter.class
})
@ActiveProfiles("test")
@DisplayName("DashboardController HTTP and security contract")
class DashboardControllerTest {

    private static final UUID SYSTEM_ID = UUID.fromString("41c972b7-baf8-457b-b87b-afb194d72f78");
    private static final OffsetDateTime TIMESTAMP =
            OffsetDateTime.of(2026, 8, 27, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private PulseOpsUserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/dashboard/summary returns the complete aggregate for a viewer")
    void shouldReturnSummaryForViewer() throws Exception {
        DashboardSummaryResponse response = new DashboardSummaryResponse(
                12,
                new BigDecimal("99.80"),
                new BigDecimal("0.15"),
                3,
                -1,
                new BigDecimal("89.40"),
                new BigDecimal("1.25"),
                17,
                9,
                2,
                1,
                "CRITICAL",
                "7d"
        );
        when(dashboardService.summary("7d", Environment.PRODUCTION)).thenReturn(response);

        mockMvc.perform(get("/api/dashboard/summary")
                        .param("period", "7d")
                        .param("environment", "PRODUCTION")
                        .with(user("viewer@pulseops.dev").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monitoredSystems").value(12))
                .andExpect(jsonPath("$.averageAvailability").value(99.8))
                .andExpect(jsonPath("$.availabilityChange").value(0.15))
                .andExpect(jsonPath("$.openIncidents").value(3))
                .andExpect(jsonPath("$.incidentChange").value(-1))
                .andExpect(jsonPath("$.averageCoverage").value(89.4))
                .andExpect(jsonPath("$.coverageChange").value(1.25))
                .andExpect(jsonPath("$.deployments").value(17))
                .andExpect(jsonPath("$.operationalSystems").value(9))
                .andExpect(jsonPath("$.degradedSystems").value(2))
                .andExpect(jsonPath("$.downSystems").value(1))
                .andExpect(jsonPath("$.overallHealth").value("CRITICAL"))
                .andExpect(jsonPath("$.period").value("7d"));

        verify(dashboardService).summary("7d", Environment.PRODUCTION);
    }

    @Test
    @DisplayName("GET /api/dashboard/latency uses 24h by default and serializes chart points")
    void shouldReturnLatencyUsingDefaultPeriod() throws Exception {
        when(dashboardService.latency("24h", null)).thenReturn(List.of(
                new LatencyPointResponse(TIMESTAMP, new BigDecimal("142.50"), 210L, 24)
        ));

        mockMvc.perform(get("/api/dashboard/latency")
                        .with(user("developer@pulseops.dev").roles("DEVELOPER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].timestamp").value("2026-08-27T12:00:00Z"))
                .andExpect(jsonPath("$[0].averageMs").value(142.5))
                .andExpect(jsonPath("$[0].p95Ms").value(210))
                .andExpect(jsonPath("$[0].samples").value(24));

        verify(dashboardService).latency("24h", null);
    }

    @Test
    @DisplayName("GET /api/dashboard/errors returns the requested period breakdown")
    void shouldReturnErrorBreakdown() throws Exception {
        when(dashboardService.errors("30d", Environment.STAGING))
                .thenReturn(new ErrorBreakdownResponse(8, 5, 3, 2, 18, "30d"));

        mockMvc.perform(get("/api/dashboard/errors")
                        .param("period", "30d")
                        .param("environment", "STAGING")
                        .with(user("admin@pulseops.dev").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverErrors").value(8))
                .andExpect(jsonPath("$.clientErrors").value(5))
                .andExpect(jsonPath("$.timeouts").value(3))
                .andExpect(jsonPath("$.others").value(2))
                .andExpect(jsonPath("$.total").value(18))
                .andExpect(jsonPath("$.period").value("30d"));

        verify(dashboardService).errors("30d", Environment.STAGING);
    }

    @Test
    @DisplayName("GET /api/dashboard/health returns system status and sparkline data")
    void shouldReturnSystemHealth() throws Exception {
        when(dashboardService.health("24h", null)).thenReturn(List.of(new SystemHealthResponse(
                SYSTEM_ID,
                "PlaySpace",
                Environment.PRODUCTION,
                SystemStatus.OPERATIONAL,
                new BigDecimal("99.95"),
                127L,
                TIMESTAMP,
                List.of(110L, 119L, 127L)
        )));

        mockMvc.perform(get("/api/dashboard/health")
                        .with(user("viewer@pulseops.dev").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(SYSTEM_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("PlaySpace"))
                .andExpect(jsonPath("$[0].environment").value("PRODUCTION"))
                .andExpect(jsonPath("$[0].status").value("OPERATIONAL"))
                .andExpect(jsonPath("$[0].uptime").value(99.95))
                .andExpect(jsonPath("$[0].latencyMs").value(127))
                .andExpect(jsonPath("$[0].lastCheckedAt").value("2026-08-27T12:00:00Z"))
                .andExpect(jsonPath("$[0].sparkline[2]").value(127));

        verify(dashboardService).health("24h", null);
    }

    @Test
    @DisplayName("GET /api/dashboard/summary returns standardized 401 when unauthenticated")
    void shouldRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Autenticação necessária"))
                .andExpect(jsonPath("$.path").value("/api/dashboard/summary"))
                .andExpect(jsonPath("$.timestamp").exists());

        verify(dashboardService, never()).summary("24h", null);
    }

    @Test
    @DisplayName("GET /api/dashboard/summary returns standardized 403 to an unsupported role")
    void shouldRejectUnsupportedRole() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .with(user("guest@pulseops.dev").roles("GUEST")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Acesso negado para este recurso"))
                .andExpect(jsonPath("$.path").value("/api/dashboard/summary"));

        verify(dashboardService, never()).summary("24h", null);
    }

    @Test
    @DisplayName("GET /api/dashboard/summary returns structured 400 for an invalid environment")
    void shouldRejectInvalidEnvironment() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")
                        .param("environment", "SANDBOX")
                        .with(user("viewer@pulseops.dev").roles("VIEWER")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message")
                        .value("A requisição possui formato ou parâmetros inválidos"))
                .andExpect(jsonPath("$.path").value("/api/dashboard/summary"));

        verifyNoInteractions(dashboardService);
    }
}
