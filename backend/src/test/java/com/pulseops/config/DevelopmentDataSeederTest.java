package com.pulseops.config;

import com.pulseops.domain.deployment.Deployment;
import com.pulseops.domain.incident.Incident;
import com.pulseops.domain.incident.IncidentSeverity;
import com.pulseops.domain.incident.IncidentStatus;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.notification.Notification;
import com.pulseops.domain.quality.TestReport;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import com.pulseops.domain.user.User;
import com.pulseops.repository.DeploymentRepository;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.IncidentRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.repository.NotificationRepository;
import com.pulseops.repository.TestReportRepository;
import com.pulseops.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevelopmentDataSeederTest {

    @Mock private UserRepository userRepository;
    @Mock private MonitoredSystemRepository systemRepository;
    @Mock private HealthCheckRepository healthCheckRepository;
    @Mock private IncidentRepository incidentRepository;
    @Mock private DeploymentRepository deploymentRepository;
    @Mock private TestReportRepository testReportRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationArguments arguments;

    @Captor private ArgumentCaptor<List<HealthCheck>> healthChecks;
    @Captor private ArgumentCaptor<List<Incident>> incidents;
    @Captor private ArgumentCaptor<List<Deployment>> deployments;
    @Captor private ArgumentCaptor<List<TestReport>> reports;
    @Captor private ArgumentCaptor<List<Notification>> notifications;

    private DevelopmentDataSeeder seeder;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-08-27T18:00:00Z"), ZoneOffset.UTC);
        seeder = new DevelopmentDataSeeder(
                userRepository, systemRepository, healthCheckRepository, incidentRepository,
                deploymentRepository, testReportRepository, notificationRepository, passwordEncoder, clock);
    }

    @Test
    void shouldCreateConsistentDevelopmentDataset() {
        when(systemRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode(any())).thenReturn("$2a$12$encoded-demo-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(systemRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        seeder.run(arguments);

        verify(systemRepository).saveAll(anyList());
        verify(healthCheckRepository).saveAll(healthChecks.capture());
        verify(incidentRepository).saveAll(incidents.capture());
        verify(deploymentRepository).saveAll(deployments.capture());
        verify(testReportRepository).saveAll(reports.capture());
        verify(notificationRepository).saveAll(notifications.capture());

        assertThat(healthChecks.getValue()).hasSize(384)
                .allSatisfy(check -> {
                    assertThat(check.getMonitoredSystem()).isNotNull();
                    assertThat(check.getCheckedAt()).isNotNull();
                    assertThat(check.getResponseTimeMs()).isNotNegative();
                });
        assertThat(incidents.getValue()).hasSize(5)
                .anySatisfy(incident -> {
                    assertThat(incident.isAutomatic()).isTrue();
                    assertThat(incident.getTitle()).contains("2.4.9");
                    assertThat(incident.getDescription()).contains("três timeouts");
                });
        assertThat(deployments.getValue()).hasSize(16)
                .allSatisfy(deployment -> assertThat(deployment.getCommitHash()).hasSize(7));
        assertThat(deployments.getValue()).anySatisfy(deployment -> {
            assertThat(deployment.getVersion()).isEqualTo("2.4.9");
            assertThat(deployment.getStatus()).isEqualTo(com.pulseops.domain.deployment.DeploymentStatus.FAILED);
            assertThat(deployment.getDescription()).contains("incidente crítico correlacionado");
        });
        assertThat(reports.getValue()).hasSize(8)
                .allSatisfy(report -> assertThat(
                        report.getPassedTests() + report.getFailedTests() + report.getSkippedTests())
                        .isEqualTo(report.getTotalTests()));
        assertThat(notifications.getValue()).hasSize(4);
    }

    @Test
    void shouldRemainIdempotentWhenSystemsAlreadyExist() {
        when(systemRepository.count()).thenReturn(4L);

        seeder.run(arguments);

        verify(userRepository, never()).save(any());
        verify(systemRepository, never()).saveAll(anyList());
        verify(healthCheckRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldPreserveResolvedAutomaticIncidentTimelineWhenRefreshingDemo() {
        MonitoredSystem playSpace = demoSystem("PlaySpace");
        MonitoredSystem logiTrack = demoSystem("LogiTrack");
        MonitoredSystem finance = demoSystem("Gestão Financeira");
        MonitoredSystem auditor = demoSystem("AI Web Auditor");
        HealthCheck check = new HealthCheck();
        check.setMonitoredSystem(auditor);
        check.setCheckedAt(OffsetDateTime.parse("2026-08-27T17:00:00Z"));
        Incident resolved = new Incident();
        resolved.setMonitoredSystem(auditor);
        resolved.setTitle("Timeout histórico");
        resolved.setStatus(IncidentStatus.RESOLVED);
        resolved.setAutomatic(true);
        resolved.setStartedAt(OffsetDateTime.parse("2026-08-27T14:00:00Z"));
        resolved.setResolvedAt(OffsetDateTime.parse("2026-08-27T15:00:00Z"));
        when(systemRepository.count()).thenReturn(4L);
        when(systemRepository.findAll()).thenReturn(List.of(playSpace, logiTrack, finance, auditor));
        when(healthCheckRepository.findAll()).thenReturn(List.of(check));
        when(incidentRepository.findAll()).thenReturn(List.of(resolved));

        seeder.run(arguments);

        verify(incidentRepository).saveAll(incidents.capture());
        Incident refreshed = incidents.getValue().getFirst();
        assertThat(refreshed.getTitle()).isEqualTo("Timeout histórico");
        assertThat(Duration.between(refreshed.getStartedAt(), refreshed.getResolvedAt())).isEqualTo(Duration.ofHours(1));
        assertThat(playSpace.getStatus()).isEqualTo(SystemStatus.OPERATIONAL);
        assertThat(logiTrack.getStatus()).isEqualTo(SystemStatus.DEGRADED);
        assertThat(finance.getStatus()).isEqualTo(SystemStatus.OPERATIONAL);
        assertThat(auditor.getStatus()).isEqualTo(SystemStatus.DOWN);
    }

    @Test
    void shouldKeepOnlyTheCorrelatedAuditorAutomaticIncidentActive() {
        MonitoredSystem playSpace = demoSystem("PlaySpace");
        MonitoredSystem logiTrack = demoSystem("LogiTrack");
        MonitoredSystem finance = demoSystem("Gestão Financeira");
        MonitoredSystem auditor = demoSystem("AI Web Auditor");
        HealthCheck check = new HealthCheck();
        check.setMonitoredSystem(auditor);
        check.setCheckedAt(OffsetDateTime.parse("2026-08-27T17:00:00Z"));

        Incident staleAutomatic = new Incident();
        staleAutomatic.setMonitoredSystem(playSpace);
        staleAutomatic.setTitle("Automated availability incident");
        staleAutomatic.setSeverity(IncidentSeverity.HIGH);
        staleAutomatic.setStatus(IncidentStatus.OPEN);
        staleAutomatic.setAutomatic(true);
        staleAutomatic.setStartedAt(OffsetDateTime.parse("2026-08-27T16:00:00Z"));

        Incident correlated = new Incident();
        correlated.setMonitoredSystem(auditor);
        correlated.setTitle("Automated availability incident");
        correlated.setSeverity(IncidentSeverity.HIGH);
        correlated.setStatus(IncidentStatus.OPEN);
        correlated.setAutomatic(true);
        correlated.setStartedAt(OffsetDateTime.parse("2026-08-27T16:30:00Z"));

        when(systemRepository.count()).thenReturn(4L);
        when(systemRepository.findAll()).thenReturn(List.of(playSpace, logiTrack, finance, auditor));
        when(healthCheckRepository.findAll()).thenReturn(List.of(check));
        when(incidentRepository.findAll()).thenReturn(List.of(staleAutomatic, correlated));

        seeder.run(arguments);

        verify(incidentRepository).saveAll(incidents.capture());
        assertThat(staleAutomatic.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(staleAutomatic.getResolvedAt()).isEqualTo(OffsetDateTime.parse("2026-08-27T18:00:00Z"));
        assertThat(staleAutomatic.getTitle()).isEqualTo("Instabilidade de disponibilidade detectada");
        assertThat(staleAutomatic.getDescription()).contains("falhas consecutivas", "automaticamente");
        assertThat(correlated.getStatus()).isEqualTo(IncidentStatus.OPEN);
        assertThat(correlated.getSeverity()).isEqualTo(IncidentSeverity.CRITICAL);
        assertThat(correlated.getTitle()).contains("2.4.9");
        assertThat(correlated.getStartedAt()).isEqualTo(OffsetDateTime.parse("2026-08-27T17:35:00Z"));
    }

    private MonitoredSystem demoSystem(String name) {
        MonitoredSystem system = new MonitoredSystem();
        system.setId(UUID.randomUUID());
        system.setName(name);
        return system;
    }
}
