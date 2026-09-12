package com.pulseops.service.integration;

import com.pulseops.exception.BusinessRuleException;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.service.monitoring.MonitoringService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.pulseops.service.integration.IntegrationServiceTest.*;

@ExtendWith(MockitoExtension.class)
class IntegrationCheckServiceTest {
    @Mock IntegrationService integrations;
    @Mock MonitoringService monitoring;
    @Mock HealthCheckRepository checks;
    IntegrationCheckService service;
    @BeforeEach void setup() { service = new IntegrationCheckService(integrations, monitoring, checks, properties(Map.of()), CLOCK); }

    @Test void rejectsUnconfiguredAndPausedSystemsWithoutNetworkIo() {
        when(integrations.resolve(IntegrationCatalog.AI_WEB_AUDITOR)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.check("ai-web-auditor")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("não configurada");
        var system = system(); system.setActive(false);
        when(integrations.resolve(IntegrationCatalog.AI_WEB_AUDITOR)).thenReturn(Optional.of(system));
        assertThatThrownBy(() -> service.check("ai-web-auditor")).isInstanceOf(BusinessRuleException.class).hasMessageContaining("pausado");
        verifyNoInteractions(monitoring, checks);
    }

    @Test void executesAndReturnsRecordedNormalizedCheck() {
        var system = system(); var check = check(system, true, NOW);
        when(integrations.resolve(IntegrationCatalog.AI_WEB_AUDITOR)).thenReturn(Optional.of(system));
        when(monitoring.checkSystem(system.getId())).thenReturn(check);
        var result = service.check("ai-web-auditor");
        assertThat(result.status()).isEqualTo("ONLINE");
        assertThat(result.responseTimeMs()).isEqualTo(120);
        assertThat(result.cached()).isFalse();
        assertThat(result.nextCheckAt()).isEqualTo(NOW.plusMinutes(1));
        verify(monitoring).checkSystem(system.getId());
    }

    @Test void reusesPersistedCheckDuringCooldownIncludingFailures() {
        var system = system(); var check = check(system, false, NOW.minusSeconds(10));
        when(integrations.resolve(IntegrationCatalog.AI_WEB_AUDITOR)).thenReturn(Optional.of(system));
        when(checks.findFirstByMonitoredSystemIdOrderByCheckedAtDesc(system.getId())).thenReturn(Optional.of(check));
        var result = service.check("ai-web-auditor");
        assertThat(result.status()).isEqualTo("OFFLINE");
        assertThat(result.cached()).isTrue();
        verifyNoInteractions(monitoring);
    }

    @Test void aNewCheckIsAllowedAtTheCooldownBoundary() {
        var system = system();
        when(integrations.resolve(IntegrationCatalog.AI_WEB_AUDITOR)).thenReturn(Optional.of(system));
        when(checks.findFirstByMonitoredSystemIdOrderByCheckedAtDesc(system.getId())).thenReturn(Optional.of(check(system, false, NOW.minusMinutes(1))));
        when(monitoring.checkSystem(system.getId())).thenReturn(check(system, true, NOW));
        assertThat(service.check("ai-web-auditor").cached()).isFalse();
    }

    @Test void cachedCheckDoesNotTraverseDetachedJpaAssociations() {
        var system = system();
        var detached = mock(com.pulseops.domain.monitoring.HealthCheck.class);
        when(detached.getCheckedAt()).thenReturn(NOW);
        when(detached.isSuccess()).thenReturn(true);
        when(integrations.resolve(IntegrationCatalog.AI_WEB_AUDITOR)).thenReturn(Optional.of(system));
        when(checks.findFirstByMonitoredSystemIdOrderByCheckedAtDesc(system.getId())).thenReturn(Optional.of(detached));
        assertThat(service.check("ai-web-auditor").status()).isEqualTo("ONLINE");
        verify(detached, never()).getMonitoredSystem();
    }
}
