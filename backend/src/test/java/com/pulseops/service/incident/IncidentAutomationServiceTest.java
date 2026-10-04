package com.pulseops.service.incident;

import com.pulseops.domain.incident.Incident;
import com.pulseops.domain.incident.IncidentSeverity;
import com.pulseops.domain.incident.IncidentStatus;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import com.pulseops.dto.monitoring.MonitoringDecision;
import com.pulseops.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IncidentAutomationService")
class IncidentAutomationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T15:00:00Z");
    private static final OffsetDateTime NOW_OFFSET = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private IncidentService incidentService;

    private IncidentAutomationService automationService;

    @BeforeEach
    void setUp() {
        automationService = new IncidentAutomationService(
                incidentRepository,
                incidentService,
                Clock.fixed(NOW, ZoneOffset.UTC),
                3,
                2,
                5
        );
    }

    @Test
    void shouldCreateHighSeverityIncidentAfterThreeConsecutiveFailures() {
        MonitoredSystem system = system();
        HealthCheck current = failedCheck(NOW_OFFSET);
        MonitoringDecision decision = new MonitoringDecision(
                SystemStatus.DOWN, false, "Health check timed out");
        when(activeIncidents(system)).thenReturn(List.of());

        automationService.evaluate(
                system,
                current,
                decision,
                List.of(failedCheck(NOW_OFFSET.minusMinutes(1)), failedCheck(NOW_OFFSET.minusMinutes(2)))
        );

        ArgumentCaptor<String> descriptionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<IncidentSeverity> severityCaptor = ArgumentCaptor.forClass(IncidentSeverity.class);
        verify(incidentService).createAutomatic(
                eq(system),
                eq(IncidentAutomationService.AUTOMATIC_INCIDENT_TITLE),
                descriptionCaptor.capture(),
                severityCaptor.capture(),
                eq(NOW_OFFSET.minusMinutes(2))
        );
        assertThat(descriptionCaptor.getValue()).contains("3 verificações consecutivas", "PlaySpace", "timed out");
        assertThat(severityCaptor.getValue()).isEqualTo(IncidentSeverity.HIGH);
    }

    @Test
    void shouldEscalateAutomaticIncidentToCriticalAtConfiguredFailureThreshold() {
        MonitoredSystem system = system();
        when(activeIncidents(system)).thenReturn(List.of());

        automationService.evaluate(
                system,
                failedCheck(null),
                new MonitoringDecision(SystemStatus.DOWN, false, "Connection refused"),
                List.of(failedCheck(null), failedCheck(null), failedCheck(null), failedCheck(null))
        );

        verify(incidentService).createAutomatic(
                eq(system), any(), any(), eq(IncidentSeverity.CRITICAL), eq(NOW_OFFSET));
    }

    @Test
    void shouldNotCreateIncidentBeforeFailureThreshold() {
        MonitoredSystem system = system();
        when(activeIncidents(system)).thenReturn(List.of());

        automationService.evaluate(
                system,
                failedCheck(NOW_OFFSET),
                new MonitoringDecision(SystemStatus.DEGRADED, false, "Unexpected HTTP status"),
                List.of(failedCheck(NOW_OFFSET.minusMinutes(1)), healthyCheck(NOW_OFFSET.minusMinutes(2)))
        );

        verify(incidentService, never()).createAutomatic(any(), any(), any(), any(), any());
    }

    @Test
    void shouldNotCreateDuplicateWhenAutomaticIncidentIsAlreadyActive() {
        MonitoredSystem system = system();
        Incident active = incident(true, IncidentStatus.INVESTIGATING);
        when(activeIncidents(system)).thenReturn(List.of(active));

        automationService.evaluate(
                system,
                failedCheck(NOW_OFFSET),
                new MonitoringDecision(SystemStatus.DOWN, false, "Down"),
                List.of(failedCheck(null), failedCheck(null), failedCheck(null))
        );

        verify(incidentService, never()).createAutomatic(any(), any(), any(), any(), any());
    }

    @Test
    void shouldEscalateExistingAutomaticIncidentAfterCriticalFailureThreshold() {
        MonitoredSystem system = system();
        Incident active = incident(true, IncidentStatus.OPEN);
        active.setSeverity(IncidentSeverity.HIGH);
        when(activeIncidents(system)).thenReturn(List.of(active));
        when(incidentRepository.save(active)).thenReturn(active);

        automationService.evaluate(
                system,
                failedCheck(NOW_OFFSET),
                new MonitoringDecision(SystemStatus.DOWN, false, "Persistent outage"),
                List.of(failedCheck(null), failedCheck(null), failedCheck(null), failedCheck(null))
        );

        assertThat(active.getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
        verify(incidentRepository).save(active);
        verify(incidentService, never()).createAutomatic(any(), any(), any(), any(), any());
    }

    @Test
    void shouldIgnoreManualIncidentWhenCheckingForAutomaticDuplicate() {
        MonitoredSystem system = system();
        Incident manual = incident(false, IncidentStatus.OPEN);
        when(activeIncidents(system)).thenReturn(List.of(manual));

        automationService.evaluate(
                system,
                failedCheck(NOW_OFFSET),
                new MonitoringDecision(SystemStatus.DOWN, false, "Down"),
                List.of(failedCheck(null), failedCheck(null))
        );

        verify(incidentService).createAutomatic(
                eq(system), any(), any(), eq(IncidentSeverity.HIGH), eq(NOW_OFFSET));
    }

    @Test
    void shouldResolveEveryActiveAutomaticIncidentAfterTwoFullyHealthyChecks() {
        MonitoredSystem system = system();
        Incident open = incident(true, IncidentStatus.OPEN);
        Incident investigating = incident(true, IncidentStatus.INVESTIGATING);
        when(activeIncidents(system)).thenReturn(List.of(open, investigating));
        HealthCheck current = healthyCheck(NOW_OFFSET);

        automationService.evaluate(
                system,
                current,
                new MonitoringDecision(SystemStatus.OPERATIONAL, true, "Recovered"),
                List.of(healthyCheck(NOW_OFFSET.minusMinutes(1)), failedCheck(NOW_OFFSET.minusMinutes(2)))
        );

        verify(incidentService, times(1)).resolve(open, NOW_OFFSET);
        verify(incidentService, times(1)).resolve(investigating, NOW_OFFSET);
    }

    @Test
    void shouldNotResolveWhenCurrentDecisionIsDegradedOrHistoryIsNotFullyHealthy() {
        MonitoredSystem system = system();
        Incident active = incident(true, IncidentStatus.OPEN);
        when(activeIncidents(system)).thenReturn(List.of(active));
        HealthCheck slowPrevious = healthyCheck(NOW_OFFSET.minusMinutes(1));
        slowPrevious.setResponseTimeMs(system.getLatencyThresholdMs() + 1);

        automationService.evaluate(
                system,
                healthyCheck(NOW_OFFSET),
                new MonitoringDecision(SystemStatus.DEGRADED, true, "High latency"),
                List.of(healthyCheck(NOW_OFFSET.minusMinutes(1)))
        );
        automationService.evaluate(
                system,
                healthyCheck(NOW_OFFSET),
                new MonitoringDecision(SystemStatus.OPERATIONAL, true, "Recovered"),
                List.of(slowPrevious)
        );

        verify(incidentService, never()).resolve(any(Incident.class), any(OffsetDateTime.class));
    }

    @Test
    void shouldRejectInvalidThresholdsAndNullRequiredInputs() {
        assertThrows(IllegalArgumentException.class, () -> new IncidentAutomationService(
                incidentRepository, incidentService, Clock.systemUTC(), 0, 2, 5));
        assertThrows(IllegalArgumentException.class, () -> new IncidentAutomationService(
                incidentRepository, incidentService, Clock.systemUTC(), 3, 2, 2));

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> automationService.evaluate(null, failedCheck(NOW_OFFSET),
                        new MonitoringDecision(SystemStatus.DOWN, false, "Down"), List.of())
        );
        assertThat(exception).hasMessage("system é obrigatório");
    }

    private List<Incident> activeIncidents(MonitoredSystem system) {
        return incidentRepository.findByMonitoredSystemIdAndStatusInOrderByStartedAtDesc(
                eq(system.getId()), any(Collection.class));
    }

    private MonitoredSystem system() {
        MonitoredSystem system = new MonitoredSystem();
        system.setId(UUID.randomUUID());
        system.setName("PlaySpace");
        system.setExpectedStatusCode(200);
        system.setLatencyThresholdMs(1_000);
        return system;
    }

    private HealthCheck failedCheck(OffsetDateTime checkedAt) {
        HealthCheck check = new HealthCheck();
        check.setCheckedAt(checkedAt);
        check.setSuccess(false);
        return check;
    }

    private HealthCheck healthyCheck(OffsetDateTime checkedAt) {
        HealthCheck check = new HealthCheck();
        check.setCheckedAt(checkedAt);
        check.setSuccess(true);
        check.setHttpStatus(200);
        check.setResponseTimeMs(150);
        return check;
    }

    private Incident incident(boolean automatic, IncidentStatus status) {
        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setAutomatic(automatic);
        incident.setStatus(status);
        incident.setStartedAt(NOW_OFFSET.minusHours(1));
        return incident;
    }
}
