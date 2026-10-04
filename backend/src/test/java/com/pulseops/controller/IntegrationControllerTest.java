package com.pulseops.controller;

import com.pulseops.config.SecurityConfig;
import com.pulseops.dto.integration.IntegrationCheckResponse;
import com.pulseops.dto.integration.IntegrationOverviewResponse;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.security.*;
import com.pulseops.service.integration.IntegrationActivityService;
import com.pulseops.service.integration.IntegrationCheckService;
import com.pulseops.service.integration.IntegrationService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(IntegrationController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class, SecurityErrorWriter.class})
@ActiveProfiles("test")
class IntegrationControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean IntegrationService integrations;
    @MockitoBean IntegrationActivityService activity;
    @MockitoBean IntegrationCheckService checks;
    @MockitoBean JwtService jwt;
    @MockitoBean PulseOpsUserDetailsService users;
    @MockitoBean DemoModeProperties demo;

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "DEVELOPER", "VIEWER"})
    void authenticatedRolesCanReadOverviewAndEvents(String role) throws Exception {
        when(integrations.overview()).thenReturn(new IntegrationOverviewResponse(List.of(), new IntegrationOverviewResponse.Summary(0, 0, 0, 0), false, OffsetDateTime.now(), "UTC"));
        mvc.perform(get("/api/integrations").with(user("reader").roles(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.summary.connected").value(0));
        mvc.perform(get("/api/integrations/events").with(user("reader").roles(role))).andExpect(status().isOk());
        mvc.perform(get("/api/integrations/ai-web-auditor/events").with(user("reader").roles(role))).andExpect(status().isOk());
        verify(activity).events("ai-web-auditor");
    }

    @Test void anonymousAndViewerCannotProbe() throws Exception {
        mvc.perform(get("/api/integrations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/integrations/ai-web-auditor/health-check")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/integrations/ai-web-auditor/health-check").with(user("viewer").roles("VIEWER"))).andExpect(status().isForbidden());
        verifyNoInteractions(checks);
    }

    @ParameterizedTest @ValueSource(strings = {"ADMIN", "DEVELOPER"})
    void writersReceiveNormalizedOfflineResultWithoutServerError(String role) throws Exception {
        var time = OffsetDateTime.now();
        when(checks.check("ai-web-auditor")).thenReturn(new IntegrationCheckResponse("ai-web-auditor", "OFFLINE", 5000L, time, "Falha de comunicação", false, time.plusMinutes(1)));
        mvc.perform(post("/api/integrations/ai-web-auditor/health-check").with(user("writer").roles(role)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("OFFLINE"))
                .andExpect(jsonPath("$.responseTimeMs").value(5000));
    }

    @Test void demoModePreventsChecksEvenForAdmin() throws Exception {
        when(demo.readOnly()).thenReturn(true);
        mvc.perform(post("/api/integrations/ai-web-auditor/health-check").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("O ambiente demonstrativo é somente leitura."));
        verifyNoInteractions(checks);
    }

    @Test void returnsStandardErrorsForUnknownAndUnconfiguredEntries() throws Exception {
        when(integrations.detail("missing")).thenThrow(new ResourceNotFoundException("Integração", "missing"));
        when(checks.check("helpdesk")).thenThrow(new BusinessRuleException("Integração não configurada"));
        mvc.perform(get("/api/integrations/missing").with(user("viewer").roles("VIEWER"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/integrations/helpdesk/health-check").with(user("admin").roles("ADMIN"))).andExpect(status().isUnprocessableEntity());
    }

    @Test void arbitraryClientUrlCannotBecomeAnOutboundTarget() throws Exception {
        mvc.perform(post("/api/integrations/ai-web-auditor/health-check")
                .param("url", "http://169.254.169.254").contentType("application/json").content("{\"url\":\"http://127.0.0.1\"}")
                .with(user("admin").roles("ADMIN"))).andExpect(status().isOk());
        verify(checks).check("ai-web-auditor");
    }
}
