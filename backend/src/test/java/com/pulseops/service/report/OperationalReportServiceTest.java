package com.pulseops.service.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulseops.domain.deployment.Deployment;
import com.pulseops.domain.deployment.DeploymentStatus;
import com.pulseops.domain.incident.Incident;
import com.pulseops.domain.incident.IncidentSeverity;
import com.pulseops.domain.incident.IncidentStatus;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.quality.TestReport;
import com.pulseops.domain.system.Environment;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import com.pulseops.dto.report.OperationalEventType;
import com.pulseops.dto.report.OperationalReportResponse;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.repository.DeploymentRepository;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.IncidentRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.repository.TestReportRepository;
import com.pulseops.service.quality.QualityService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OperationalReportServiceTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-27T15:00:00Z");

    @Mock MonitoredSystemRepository systemRepository;
    @Mock HealthCheckRepository healthCheckRepository;
    @Mock IncidentRepository incidentRepository;
    @Mock DeploymentRepository deploymentRepository;
    @Mock TestReportRepository testReportRepository;
    private OperationalReportService service;

    @BeforeEach
    void setUp() {
        QualityService qualityService = new QualityService(testReportRepository, systemRepository,
                Clock.fixed(Instant.parse("2026-08-27T15:00:00Z"), ZoneOffset.UTC));
        service = new OperationalReportService(systemRepository, healthCheckRepository, incidentRepository,
                deploymentRepository, testReportRepository, qualityService,
                Clock.fixed(Instant.parse("2026-08-27T15:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void shouldAggregateKpisAndSortMixedFeed() {
        MonitoredSystem system = system();
        when(systemRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(system));
        when(healthCheckRepository.countForOperationalReport(any(), any(), eq(Environment.PRODUCTION))).thenReturn(10L);
        when(healthCheckRepository.countSuccessfulForOperationalReport(any(), any(), eq(Environment.PRODUCTION))).thenReturn(9L);
        when(incidentRepository.countActiveForOperationalReport(any(), eq(Environment.PRODUCTION))).thenReturn(1L);
        when(incidentRepository.countOpenedForOperationalReport(any(), any(), eq(Environment.PRODUCTION))).thenReturn(2L);
        when(deploymentRepository.countForOperationalReport(any(), any(), eq(Environment.PRODUCTION))).thenReturn(4L);
        when(deploymentRepository.countByStatusForOperationalReport(any(), any(), eq(Environment.PRODUCTION), eq(DeploymentStatus.SUCCESS))).thenReturn(3L);
        TestReport report = report(system);
        when(testReportRepository.findLatestForOperationalReport(any(), eq(Environment.PRODUCTION))).thenReturn(List.of(report));
        when(healthCheckRepository.findFeedForOperationalReport(any(), any(), eq(Environment.PRODUCTION), any()))
                .thenReturn(List.of(check(system)));
        when(incidentRepository.findFeedForOperationalReport(any(), any(), eq(Environment.PRODUCTION), any()))
                .thenReturn(List.of(incident(system)));
        when(deploymentRepository.findFeedForOperationalReport(any(), any(), eq(Environment.PRODUCTION), any()))
                .thenReturn(List.of(deployment(system)));
        when(testReportRepository.findFeedForOperationalReport(any(), any(), eq(Environment.PRODUCTION), any()))
                .thenReturn(List.of(report));

        OperationalReportResponse result = service.generate("7D", Environment.PRODUCTION);

        assertThat(result.period()).isEqualTo("7d");
        assertThat(result.windowStart()).isEqualTo(NOW.minusDays(7));
        assertThat(result.kpis().availability()).isEqualByComparingTo("90.00");
        assertThat(result.kpis().deploymentSuccessRate()).isEqualByComparingTo("75.00");
        assertThat(result.kpis().testPassRate()).isEqualByComparingTo("99.00");
        assertThat(result.feed()).extracting(event -> event.type())
                .containsExactly(OperationalEventType.INCIDENT, OperationalEventType.DEPLOYMENT,
                        OperationalEventType.QUALITY, OperationalEventType.HEALTH_CHECK);
        assertThat(result.feed()).extracting(event -> event.source())
                .containsExactly("Equipe de Operações", "GitHub Actions", "Pipeline de Qualidade", "Scheduler PulseOps");
        assertThat(result.feed()).extracting(event -> event.title())
                .contains("Health check bem-sucedido");
        verify(healthCheckRepository).countForOperationalReport(NOW.minusDays(7), NOW, Environment.PRODUCTION);
    }

    @Test
    void shouldRejectUnknownPeriodBeforeQueryingRepositories() {
        assertThatThrownBy(() -> service.generate("90d", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("24h, 7d or 30d");
    }

    private MonitoredSystem system() {
        MonitoredSystem system = new MonitoredSystem();
        system.setId(UUID.randomUUID());
        system.setName("PlaySpace");
        system.setEnvironment(Environment.PRODUCTION);
        system.setStatus(SystemStatus.OPERATIONAL);
        system.setActive(true);
        return system;
    }

    private HealthCheck check(MonitoredSystem system) {
        HealthCheck check = new HealthCheck();
        check.setId(UUID.randomUUID());
        check.setMonitoredSystem(system);
        check.setCheckedAt(NOW.minusHours(4));
        check.setHttpStatus(200);
        check.setResponseTimeMs(120);
        check.setSuccess(true);
        return check;
    }

    private Incident incident(MonitoredSystem system) {
        Incident incident = new Incident();
        incident.setId(UUID.randomUUID());
        incident.setMonitoredSystem(system);
        incident.setTitle("Latência elevada");
        incident.setSeverity(IncidentSeverity.HIGH);
        incident.setStatus(IncidentStatus.INVESTIGATING);
        incident.setStartedAt(NOW.minusHours(1));
        return incident;
    }

    private Deployment deployment(MonitoredSystem system) {
        Deployment deployment = new Deployment();
        deployment.setId(UUID.randomUUID());
        deployment.setMonitoredSystem(system);
        deployment.setVersion("2.4.1");
        deployment.setEnvironment(Environment.PRODUCTION);
        deployment.setStatus(DeploymentStatus.SUCCESS);
        deployment.setDeployedAt(NOW.minusHours(2));
        return deployment;
    }

    private TestReport report(MonitoredSystem system) {
        TestReport report = new TestReport();
        report.setId(UUID.randomUUID());
        report.setMonitoredSystem(system);
        report.setTotalTests(100);
        report.setPassedTests(99);
        report.setFailedTests(1);
        report.setSkippedTests(0);
        report.setLineCoverage(new BigDecimal("91.00"));
        report.setBranchCoverage(new BigDecimal("86.00"));
        report.setGeneratedAt(NOW.minusHours(3));
        return report;
    }
}
