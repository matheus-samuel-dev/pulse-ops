package com.pulseops.service.monitoring;

import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import com.pulseops.dto.monitoring.HealthProbeResult;
import com.pulseops.dto.monitoring.MonitoringDecision;
import com.pulseops.dto.monitoring.ProbeFailureType;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.service.incident.IncidentAutomationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MonitoringPersistenceService")
class MonitoringPersistenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T15:00:00Z");

    @Mock
    private MonitoredSystemRepository systemRepository;
    @Mock
    private HealthCheckRepository healthCheckRepository;
    @Mock
    private MonitoringStatusEvaluator statusEvaluator;
    @Mock
    private IncidentAutomationService incidentAutomationService;

    private MonitoringPersistenceService persistenceService;

    @BeforeEach
    void setUp() {
        persistenceService = new MonitoringPersistenceService(
                systemRepository,
                healthCheckRepository,
                statusEvaluator,
                incidentAutomationService,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void shouldAtomicallyRecordCheckStatusAndIncidentDecisionUsingLockedSystem() {
        UUID systemId = UUID.randomUUID();
        MonitoredSystem probeTarget = activeSystem(systemId);
        MonitoredSystem lockedSystem = activeSystem(systemId);
        List<HealthCheck> history = List.of(check(false));
        HealthProbeResult probe = HealthProbeResult.response(200, 245);
        MonitoringDecision decision = new MonitoringDecision(
                SystemStatus.OPERATIONAL, true, "Health check succeeded");
        when(systemRepository.findByIdForUpdate(systemId)).thenReturn(Optional.of(lockedSystem));
        when(healthCheckRepository.findTop10ByMonitoredSystemIdOrderByCheckedAtDesc(systemId))
                .thenReturn(history);
        when(statusEvaluator.evaluate(probeTarget, probe, history)).thenReturn(decision);
        when(healthCheckRepository.save(any(HealthCheck.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HealthCheck result = persistenceService.record(probeTarget, probe);

        ArgumentCaptor<HealthCheck> checkCaptor = ArgumentCaptor.forClass(HealthCheck.class);
        verify(healthCheckRepository).save(checkCaptor.capture());
        HealthCheck saved = checkCaptor.getValue();
        assertThat(result).isSameAs(saved);
        assertThat(saved.getMonitoredSystem()).isSameAs(lockedSystem);
        assertThat(saved.getCheckedAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC));
        assertThat(saved.getHttpStatus()).isEqualTo(200);
        assertThat(saved.getResponseTimeMs()).isEqualTo(245);
        assertThat(saved.isSuccess()).isTrue();
        assertThat(saved.getErrorMessage()).isNull();
        assertThat(lockedSystem.getStatus()).isEqualTo(SystemStatus.OPERATIONAL);

        InOrder order = inOrder(systemRepository, healthCheckRepository, incidentAutomationService);
        order.verify(systemRepository).findByIdForUpdate(systemId);
        order.verify(healthCheckRepository).findTop10ByMonitoredSystemIdOrderByCheckedAtDesc(systemId);
        order.verify(healthCheckRepository).save(saved);
        order.verify(systemRepository).save(lockedSystem);
        order.verify(incidentAutomationService).evaluate(lockedSystem, saved, decision, history);
    }

    @Test
    void shouldPersistSanitizedFailureReason() {
        UUID systemId = UUID.randomUUID();
        MonitoredSystem target = activeSystem(systemId);
        MonitoredSystem lockedSystem = activeSystem(systemId);
        HealthProbeResult probe = HealthProbeResult.failure(
                ProbeFailureType.TIMEOUT, 5_000, "Read timed out");
        MonitoringDecision decision = new MonitoringDecision(
                SystemStatus.DOWN, false, "Health check timed out");
        stubRecord(target, lockedSystem, probe, decision);

        HealthCheck result = persistenceService.record(target, probe);

        assertThat(result.getHttpStatus()).isNull();
        assertThat(result.getResponseTimeMs()).isEqualTo(5_000);
        assertThat(result.getErrorMessage()).isEqualTo(decision.reason());
        assertThat(lockedSystem.getStatus()).isEqualTo(SystemStatus.DOWN);
    }

    @Test
    void shouldUseDecisionReasonWhenProbeHasNoErrorDetail() {
        UUID systemId = UUID.randomUUID();
        MonitoredSystem target = activeSystem(systemId);
        MonitoredSystem lockedSystem = activeSystem(systemId);
        HealthProbeResult probe = HealthProbeResult.response(503, 80);
        MonitoringDecision decision = new MonitoringDecision(
                SystemStatus.DEGRADED, false, "Unexpected HTTP status 503");
        stubRecord(target, lockedSystem, probe, decision);

        HealthCheck result = persistenceService.record(target, probe);

        assertThat(result.getErrorMessage()).isEqualTo(decision.reason());
    }

    @Test
    void shouldDiscardResultWhenSystemWasDeactivatedWhileProbeWasRunning() {
        UUID systemId = UUID.randomUUID();
        MonitoredSystem target = activeSystem(systemId);
        MonitoredSystem lockedSystem = activeSystem(systemId);
        lockedSystem.setActive(false);
        when(systemRepository.findByIdForUpdate(systemId)).thenReturn(Optional.of(lockedSystem));

        assertThatThrownBy(() -> persistenceService.record(target, HealthProbeResult.response(200, 50)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Sistemas inativos não podem ser monitorados");

        verifyNoInteractions(healthCheckRepository, statusEvaluator, incidentAutomationService);
        verify(systemRepository, never()).save(any());
    }

    @Test
    void shouldDiscardResultWhenSystemWasDeletedWhileProbeWasRunning() {
        UUID systemId = UUID.randomUUID();
        MonitoredSystem target = activeSystem(systemId);
        when(systemRepository.findByIdForUpdate(systemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> persistenceService.record(target, HealthProbeResult.response(200, 50)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(systemId.toString());

        verifyNoInteractions(healthCheckRepository, statusEvaluator, incidentAutomationService);
    }

    @Test
    void shouldRejectMissingProbeInputsBeforeUsingRepositories() {
        assertThatNullPointerException()
                .isThrownBy(() -> persistenceService.record(null, HealthProbeResult.response(200, 50)))
                .withMessage("probe target é obrigatório");
        assertThatNullPointerException()
                .isThrownBy(() -> persistenceService.record(activeSystem(UUID.randomUUID()), null))
                .withMessage("probe result é obrigatório");

        verifyNoInteractions(systemRepository, healthCheckRepository, statusEvaluator, incidentAutomationService);
    }

    private void stubRecord(
            MonitoredSystem probeTarget,
            MonitoredSystem lockedSystem,
            HealthProbeResult probe,
            MonitoringDecision decision
    ) {
        when(systemRepository.findByIdForUpdate(probeTarget.getId())).thenReturn(Optional.of(lockedSystem));
        when(healthCheckRepository.findTop10ByMonitoredSystemIdOrderByCheckedAtDesc(probeTarget.getId()))
                .thenReturn(List.of());
        when(statusEvaluator.evaluate(probeTarget, probe, List.of())).thenReturn(decision);
        when(healthCheckRepository.save(any(HealthCheck.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private MonitoredSystem activeSystem(UUID id) {
        MonitoredSystem system = new MonitoredSystem();
        system.setId(id);
        system.setName("PlaySpace");
        system.setActive(true);
        system.setStatus(SystemStatus.UNKNOWN);
        system.setExpectedStatusCode(200);
        system.setLatencyThresholdMs(1_000);
        return system;
    }

    private HealthCheck check(boolean success) {
        HealthCheck check = new HealthCheck();
        check.setSuccess(success);
        return check;
    }
}
