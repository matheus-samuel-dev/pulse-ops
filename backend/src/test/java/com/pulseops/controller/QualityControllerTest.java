package com.pulseops.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pulseops.config.SecurityConfig;
import com.pulseops.domain.quality.QualityClassification;
import com.pulseops.domain.system.Environment;
import com.pulseops.dto.quality.QualityOverviewResponse;
import com.pulseops.dto.quality.QualityReportResponse;
import com.pulseops.security.JwtAuthenticationFilter;
import com.pulseops.security.JwtService;
import com.pulseops.security.PulseOpsUserDetailsService;
import com.pulseops.security.RestAccessDeniedHandler;
import com.pulseops.security.RestAuthenticationEntryPoint;
import com.pulseops.security.SecurityErrorWriter;
import com.pulseops.service.quality.QualityService;
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

@WebMvcTest(QualityController.class)
@Import({
        SecurityConfig.class,
        JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        SecurityErrorWriter.class
})
@ActiveProfiles("test")
@DisplayName("QualityController HTTP and security contract")
class QualityControllerTest {

    private static final UUID SYSTEM_ID = UUID.fromString("41c972b7-baf8-457b-b87b-afb194d72f78");
    private static final UUID REPORT_ID = UUID.fromString("c1735ff6-c530-4ec1-88b5-2fac351ba411");
    private static final OffsetDateTime GENERATED_AT =
            OffsetDateTime.of(2026, 8, 27, 12, 0, 0, 0, ZoneOffset.UTC);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private QualityService qualityService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private PulseOpsUserDetailsService userDetailsService;

    @Test
    @DisplayName("GET /api/quality/overview returns the aggregate and per-system quality for a viewer")
    void shouldReturnOverviewForViewer() throws Exception {
        QualityReportResponse systemReport = report();
        when(qualityService.getOverview("30d",null,null)).thenReturn(new QualityOverviewResponse(
                2, 1, 1,
                324, 323, 1, 0,
                new BigDecimal("99.69"),
                new BigDecimal("92.40"),
                new BigDecimal("87.10"),
                new BigDecimal("90.28"),
                QualityClassification.GOOD,
                List.of(systemReport)));

        mockMvc.perform(get("/api/quality/overview")
                        .with(user("viewer@pulseops.dev").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monitoredSystems").value(2))
                .andExpect(jsonPath("$.systemsWithReports").value(1))
                .andExpect(jsonPath("$.systemsWithoutReports").value(1))
                .andExpect(jsonPath("$.totalTests").value(324))
                .andExpect(jsonPath("$.passRate").value(99.69))
                .andExpect(jsonPath("$.averageCoverageScore").value(90.28))
                .andExpect(jsonPath("$.classification").value("GOOD"))
                .andExpect(jsonPath("$.systems[0].systemId").value(SYSTEM_ID.toString()))
                .andExpect(jsonPath("$.systems[0].systemName").value("PlaySpace"))
                .andExpect(jsonPath("$.systems[0].generatedAt").value("2026-08-27T12:00:00Z"));

        verify(qualityService).getOverview("30d",null,null);
    }

    @Test
    @DisplayName("GET /api/quality/history forwards filters and returns a chronological chart contract")
    void shouldReturnFilteredHistoryForDeveloper() throws Exception {
        when(qualityService.getHistory(SYSTEM_ID, "7d")).thenReturn(List.of(report()));

        mockMvc.perform(get("/api/quality/history")
                        .param("systemId", SYSTEM_ID.toString())
                        .param("period", "7d")
                        .with(user("developer@pulseops.dev").roles("DEVELOPER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].reportId").value(REPORT_ID.toString()))
                .andExpect(jsonPath("$[0].totalTests").value(324))
                .andExpect(jsonPath("$[0].passedTests").value(323))
                .andExpect(jsonPath("$[0].failedTests").value(1))
                .andExpect(jsonPath("$[0].lineCoverage").value(92.4))
                .andExpect(jsonPath("$[0].branchCoverage").value(87.1))
                .andExpect(jsonPath("$[0].classification").value("GOOD"));

        verify(qualityService).getHistory(SYSTEM_ID, "7d");
    }

    @Test
    @DisplayName("GET /api/quality/history defaults to 30d")
    void shouldUseDefaultHistoryPeriod() throws Exception {
        when(qualityService.getHistory(null, "30d")).thenReturn(List.of());

        mockMvc.perform(get("/api/quality/history")
                        .with(user("viewer@pulseops.dev").roles("VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        verify(qualityService).getHistory(null, "30d");
    }

    @Test
    @DisplayName("GET /api/quality/overview requires authentication")
    void shouldRequireAuthenticationForOverview() throws Exception {
        mockMvc.perform(get("/api/quality/overview"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value("/api/quality/overview"));

        verify(qualityService, never()).getOverview("30d",null,null);
    }

    private QualityReportResponse report() {
        return new QualityReportResponse(
                SYSTEM_ID,
                "PlaySpace",
                Environment.PRODUCTION,
                REPORT_ID,
                GENERATED_AT,
                324,
                323,
                1,
                0,
                new BigDecimal("99.69"),
                new BigDecimal("92.40"),
                new BigDecimal("87.10"),
                new BigDecimal("90.28"),
                QualityClassification.GOOD, "API_IMPORT");
    }
}
