package com.pulseops.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pulseops.config.JpaAuditingConfiguration;
import com.pulseops.domain.deployment.Deployment;
import com.pulseops.domain.deployment.DeploymentStatus;
import com.pulseops.domain.incident.Incident;
import com.pulseops.domain.incident.IncidentSeverity;
import com.pulseops.domain.incident.IncidentStatus;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.notification.Notification;
import com.pulseops.domain.notification.NotificationType;
import com.pulseops.domain.quality.TestReport;
import com.pulseops.domain.system.Environment;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import com.pulseops.domain.user.User;
import com.pulseops.domain.user.UserRole;
import com.pulseops.repository.DeploymentRepository;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.IncidentRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.repository.NotificationRepository;
import com.pulseops.repository.TestReportRepository;
import com.pulseops.repository.UserRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest(showSql = false, properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfiguration.class)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
@DisplayName("PostgreSQL repositories and Flyway integration")
class PostgreSqlRepositoryIntegrationTest {

    private static final OffsetDateTime REFERENCE_TIME =
            OffsetDateTime.of(2026, 8, 27, 12, 0, 0, 0, ZoneOffset.UTC);

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pulseops_integration")
            .withUsername("pulseops_test")
            .withPassword("pulseops_test");

    @DynamicPropertySource
    static void configurePostgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MonitoredSystemRepository monitoredSystemRepository;

    @Autowired
    private HealthCheckRepository healthCheckRepository;

    @Autowired
    private IncidentRepository incidentRepository;

    @Autowired
    private DeploymentRepository deploymentRepository;

    @Autowired
    private TestReportRepository testReportRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void cleanDomainTables() {
        notificationRepository.deleteAllInBatch();
        testReportRepository.deleteAllInBatch();
        deploymentRepository.deleteAllInBatch();
        incidentRepository.deleteAllInBatch();
        healthCheckRepository.deleteAllInBatch();
        monitoredSystemRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("runs all core Flyway migrations and creates the expected PostgreSQL schema")
    void shouldRunFlywayMigrationsAndCreateExpectedSchema() {
        List<String> successfulVersions = jdbcTemplate.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = TRUE AND version IS NOT NULL "
                        + "ORDER BY installed_rank",
                String.class
        );
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'",
                String.class
        );
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'",
                String.class
        );

        assertThat(successfulVersions).contains("1", "2", "3");
        assertThat(tables).contains(
                "app_users",
                "monitored_systems",
                "health_checks",
                "incidents",
                "deployments",
                "test_reports",
                "notifications"
        );
        assertThat(indexes).contains(
                "uk_app_users_email_lower",
                "idx_health_checks_system_checked",
                "idx_incidents_system_status",
                "idx_test_reports_system_generated",
                "idx_test_reports_system_created"
        );
    }

    @Test
    void integrationQueriesScopeAndLimitHealthEventsAndExcludeTheNextDay() {
        var bound = monitoredSystemRepository.saveAndFlush(monitoredSystem("Bound", Environment.PRODUCTION, SystemStatus.OPERATIONAL, true));
        var unrelated = monitoredSystemRepository.saveAndFlush(monitoredSystem("Unrelated", Environment.PRODUCTION, SystemStatus.OPERATIONAL, true));
        healthCheckRepository.saveAllAndFlush(List.of(
                healthCheck(bound, REFERENCE_TIME, true, 200, 40),
                healthCheck(bound, REFERENCE_TIME.plusMinutes(1), false, 503, 50),
                healthCheck(bound, REFERENCE_TIME.plusDays(1), true, 200, 30),
                healthCheck(unrelated, REFERENCE_TIME.plusMinutes(2), true, 200, 10)));
        var ids = List.of(bound.getId());
        assertThat(healthCheckRepository.countByMonitoredSystemIdInAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
                ids, REFERENCE_TIME, REFERENCE_TIME.plusDays(1))).isEqualTo(2);
        var feed = healthCheckRepository.findByMonitoredSystemIdInAndCheckedAtBetweenOrderByCheckedAtDesc(
                ids, REFERENCE_TIME, REFERENCE_TIME.plusHours(1), org.springframework.data.domain.PageRequest.of(0, 1));
        assertThat(feed).hasSize(1);
        assertThat(feed.getFirst().isSuccess()).isFalse();
        assertThat(healthCheckRepository.findFirstByMonitoredSystemIdAndSuccessFalseOrderByCheckedAtDesc(bound.getId()))
                .get().extracting(HealthCheck::getCheckedAt).isEqualTo(REFERENCE_TIME.plusMinutes(1));
    }

    @Test
    void integrationReportQueriesUseReceiptTimeInsteadOfGenerationTime() {
        var bound = monitoredSystemRepository.saveAndFlush(monitoredSystem("Bound", Environment.PRODUCTION, SystemStatus.OPERATIONAL, true));
        var report = testReportRepository.saveAndFlush(testReport(bound, REFERENCE_TIME.minusDays(20), 1, 1, 0, 0, "90", "85"));
        jdbcTemplate.update("update test_reports set created_at = ? where id = ?", REFERENCE_TIME, report.getId());
        var ids = List.of(bound.getId());
        assertThat(testReportRepository.countByMonitoredSystemIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(ids, REFERENCE_TIME, REFERENCE_TIME.plusDays(1))).isEqualTo(1);
        assertThat(testReportRepository.findByMonitoredSystemIdInAndCreatedAtBetweenOrderByCreatedAtDesc(ids,
                REFERENCE_TIME, REFERENCE_TIME.plusHours(1), org.springframework.data.domain.PageRequest.of(0, 12))).hasSize(1);
        assertThat(testReportRepository.findFirstByMonitoredSystemIdOrderByCreatedAtDesc(bound.getId())).isPresent();
        assertThat(testReportRepository.countByMonitoredSystemIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(ids, REFERENCE_TIME.minusDays(1), REFERENCE_TIME)).isZero();
    }

    @Test
    @DisplayName("persists audited users and performs case-insensitive lookup and sorting")
    void shouldPersistAndQueryUsers() {
        User zulu = userRepository.saveAndFlush(user("Zulu Operator", "zulu@pulseops.dev", UserRole.VIEWER));
        User alpha = userRepository.saveAndFlush(user("Alpha Admin", "alpha@pulseops.dev", UserRole.ADMIN));

        assertThat(alpha.getId()).isNotNull();
        assertThat(alpha.getCreatedAt()).isNotNull();
        assertThat(alpha.getUpdatedAt()).isNotNull();
        assertThat(userRepository.findByEmailIgnoreCase("ALPHA@PULSEOPS.DEV"))
                .contains(alpha);
        assertThat(userRepository.existsByEmailIgnoreCase("zULu@pulseops.dev")).isTrue();
        assertThat(userRepository.findAllByOrderByNameAsc())
                .extracting(User::getName)
                .containsExactly("Alpha Admin", "Zulu Operator");
        assertThat(zulu.getRole()).isEqualTo(UserRole.VIEWER);
    }

    @Test
    @DisplayName("enforces the case-insensitive email uniqueness created by Flyway")
    void shouldRejectEmailThatDiffersOnlyByCase() {
        userRepository.saveAndFlush(user("First User", "quality@pulseops.dev", UserRole.DEVELOPER));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                user("Second User", "QUALITY@PULSEOPS.DEV", UserRole.VIEWER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("filters active monitored systems by environment and status")
    void shouldQueryMonitoredSystemsByOperationalFilters() {
        MonitoredSystem zulu = monitoredSystem("Zulu API", Environment.PRODUCTION, SystemStatus.OPERATIONAL, true);
        MonitoredSystem alpha = monitoredSystem("Alpha API", Environment.PRODUCTION, SystemStatus.DEGRADED, true);
        MonitoredSystem staging = monitoredSystem("Staging API", Environment.STAGING, SystemStatus.UNKNOWN, true);
        MonitoredSystem inactive = monitoredSystem("Retired API", Environment.PRODUCTION, SystemStatus.DOWN, false);
        monitoredSystemRepository.saveAllAndFlush(List.of(zulu, alpha, staging, inactive));

        assertThat(monitoredSystemRepository.findAllByActiveTrueOrderByNameAsc())
                .extracting(MonitoredSystem::getName)
                .containsExactly("Alpha API", "Staging API", "Zulu API");
        assertThat(monitoredSystemRepository.findAllByEnvironmentAndActiveTrueOrderByNameAsc(Environment.PRODUCTION))
                .extracting(MonitoredSystem::getName)
                .containsExactly("Alpha API", "Zulu API");
        assertThat(monitoredSystemRepository.findByNameIgnoreCase("zULU api")).contains(zulu);
        assertThat(monitoredSystemRepository.countByStatus(SystemStatus.DOWN)).isEqualTo(1);
    }

    @Test
    @DisplayName("queries health-check history in chronological order and calculates persisted counts")
    void shouldQueryHealthCheckHistoryAndCounts() {
        MonitoredSystem system = monitoredSystemRepository.saveAndFlush(
                monitoredSystem("Latency API", Environment.PRODUCTION, SystemStatus.DEGRADED, true));
        HealthCheck oldest = healthCheck(system, REFERENCE_TIME.minusMinutes(20), true, 200, 120);
        HealthCheck failed = healthCheck(system, REFERENCE_TIME.minusMinutes(10), false, 503, 950);
        HealthCheck newest = healthCheck(system, REFERENCE_TIME.minusMinutes(2), true, 200, 180);
        healthCheckRepository.saveAllAndFlush(List.of(newest, oldest, failed));

        List<HealthCheck> period = healthCheckRepository
                .findByMonitoredSystemIdAndCheckedAtBetweenOrderByCheckedAtAsc(
                        system.getId(), REFERENCE_TIME.minusHours(1), REFERENCE_TIME);

        assertThat(period).extracting(HealthCheck::getCheckedAt)
                .containsExactly(oldest.getCheckedAt(), failed.getCheckedAt(), newest.getCheckedAt());
        assertThat(healthCheckRepository.findTop10ByMonitoredSystemIdOrderByCheckedAtDesc(system.getId()))
                .extracting(HealthCheck::getCheckedAt)
                .containsExactly(newest.getCheckedAt(), failed.getCheckedAt(), oldest.getCheckedAt());
        assertThat(healthCheckRepository.countByMonitoredSystemIdAndCheckedAtBetween(
                system.getId(), REFERENCE_TIME.minusHours(1), REFERENCE_TIME)).isEqualTo(3);
        assertThat(healthCheckRepository.countByMonitoredSystemIdAndSuccessTrueAndCheckedAtBetween(
                system.getId(), REFERENCE_TIME.minusHours(1), REFERENCE_TIME)).isEqualTo(2);
    }

    @Test
    @DisplayName("finds active incidents and orders deployments by their operational timestamps")
    void shouldQueryIncidentsAndDeployments() {
        MonitoredSystem system = monitoredSystemRepository.saveAndFlush(
                monitoredSystem("Checkout API", Environment.PRODUCTION, SystemStatus.DOWN, true));
        Incident resolved = incident(
                system, "Resolved latency", IncidentSeverity.MEDIUM, IncidentStatus.RESOLVED,
                REFERENCE_TIME.minusHours(4), false);
        resolved.setResolvedAt(REFERENCE_TIME.minusHours(3));
        Incident investigating = incident(
                system, "Elevated errors", IncidentSeverity.HIGH, IncidentStatus.INVESTIGATING,
                REFERENCE_TIME.minusHours(2), false);
        Incident open = incident(
                system, "Automated availability incident: Checkout API", IncidentSeverity.CRITICAL,
                IncidentStatus.OPEN, REFERENCE_TIME.minusHours(1), true);
        incidentRepository.saveAllAndFlush(List.of(resolved, investigating, open));

        Deployment older = deployment(
                system, "v2.0.0", DeploymentStatus.SUCCESS, REFERENCE_TIME.minusDays(2), 90L);
        Deployment latest = deployment(
                system, "v2.1.0", DeploymentStatus.FAILED, REFERENCE_TIME.minusHours(3), 45L);
        deploymentRepository.saveAllAndFlush(List.of(older, latest));

        assertThat(incidentRepository.findByMonitoredSystemIdAndStatusInOrderByStartedAtDesc(
                system.getId(), List.of(IncidentStatus.OPEN, IncidentStatus.INVESTIGATING)))
                .extracting(Incident::getTitle)
                .containsExactly(open.getTitle(), investigating.getTitle());
        assertThat(incidentRepository.findFirstByMonitoredSystemIdAndStatusInOrderByStartedAtDesc(
                system.getId(), List.of(IncidentStatus.OPEN, IncidentStatus.INVESTIGATING)))
                .contains(open);
        assertThat(incidentRepository.countByStatusIn(List.of(IncidentStatus.OPEN, IncidentStatus.INVESTIGATING)))
                .isEqualTo(2);
        assertThat(deploymentRepository.findByMonitoredSystemIdOrderByDeployedAtDesc(system.getId()))
                .extracting(Deployment::getVersion)
                .containsExactly("v2.1.0", "v2.0.0");
        assertThat(deploymentRepository.findAllByDeployedAtBetweenOrderByDeployedAtDesc(
                REFERENCE_TIME.minusDays(1), REFERENCE_TIME))
                .containsExactly(latest);
        assertThat(deploymentRepository.countByStatusIn(List.of(DeploymentStatus.FAILED))).isEqualTo(1);
    }

    @Test
    @DisplayName("returns the newest quality report and unread notifications for a user")
    void shouldQueryQualityReportsAndNotifications() {
        User owner = userRepository.saveAndFlush(user("Quality Owner", "owner@pulseops.dev", UserRole.DEVELOPER));
        MonitoredSystem system = monitoredSystemRepository.saveAndFlush(
                monitoredSystem("Quality API", Environment.PRODUCTION, SystemStatus.OPERATIONAL, true));
        TestReport older = testReport(system, REFERENCE_TIME.minusDays(7), 90, 88, 2, 0, "86.50", "79.40");
        TestReport latest = testReport(system, REFERENCE_TIME, 100, 100, 0, 0, "93.70", "89.20");
        testReportRepository.saveAllAndFlush(List.of(latest, older));

        Notification read = notification(owner, "Deploy complete", NotificationType.SUCCESS, true);
        Notification unread = notification(owner, "Coverage improved", NotificationType.QUALITY, false);
        notificationRepository.saveAllAndFlush(List.of(read, unread));

        assertThat(testReportRepository.findFirstByMonitoredSystemIdOrderByGeneratedAtDesc(system.getId()))
                .contains(latest);
        assertThat(testReportRepository.findByMonitoredSystemIdOrderByGeneratedAtDesc(system.getId()))
                .extracting(TestReport::getLineCoverage)
                .containsExactly(new BigDecimal("93.70"), new BigDecimal("86.50"));
        assertThat(notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(owner.getId()))
                .containsExactly(unread);
        assertThat(notificationRepository.countByUserIdAndReadFalse(owner.getId())).isEqualTo(1);
        assertThat(unread.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("enforces consistency between total and categorized test counts at database level")
    void shouldRejectInconsistentTestReportCounts() {
        MonitoredSystem system = monitoredSystemRepository.saveAndFlush(
                monitoredSystem("Constraint API", Environment.STAGING, SystemStatus.UNKNOWN, true));
        TestReport inconsistent = testReport(
                system, REFERENCE_TIME, 10, 8, 1, 0, "70.00", "60.00");

        assertThatThrownBy(() -> testReportRepository.saveAndFlush(inconsistent))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User user(String name, String email, UserRole role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("$2a$10$integration.test.hash.not.for.production");
        user.setRole(role);
        return user;
    }

    private MonitoredSystem monitoredSystem(
            String name,
            Environment environment,
            SystemStatus status,
            boolean active
    ) {
        MonitoredSystem system = new MonitoredSystem();
        system.setName(name);
        system.setDescription("Integration-test monitored system");
        system.setBaseUrl("https://" + name.toLowerCase().replace(' ', '-') + ".example.test");
        system.setHealthEndpoint("/actuator/health");
        system.setEnvironment(environment);
        system.setStatus(status);
        system.setActive(active);
        system.setExpectedStatusCode(200);
        system.setTimeoutMs(3000);
        system.setLatencyThresholdMs(500);
        system.setTargetAvailability(new BigDecimal("99.900"));
        return system;
    }

    private HealthCheck healthCheck(
            MonitoredSystem system,
            OffsetDateTime checkedAt,
            boolean success,
            Integer httpStatus,
            long responseTimeMs
    ) {
        HealthCheck healthCheck = new HealthCheck();
        healthCheck.setMonitoredSystem(system);
        healthCheck.setCheckedAt(checkedAt);
        healthCheck.setSuccess(success);
        healthCheck.setHttpStatus(httpStatus);
        healthCheck.setResponseTimeMs(responseTimeMs);
        healthCheck.setErrorMessage(success ? null : "Unexpected HTTP status");
        return healthCheck;
    }

    private Incident incident(
            MonitoredSystem system,
            String title,
            IncidentSeverity severity,
            IncidentStatus status,
            OffsetDateTime startedAt,
            boolean automatic
    ) {
        Incident incident = new Incident();
        incident.setMonitoredSystem(system);
        incident.setTitle(title);
        incident.setDescription("Integration-test incident");
        incident.setSeverity(severity);
        incident.setStatus(status);
        incident.setStartedAt(startedAt);
        incident.setAutomatic(automatic);
        return incident;
    }

    private Deployment deployment(
            MonitoredSystem system,
            String version,
            DeploymentStatus status,
            OffsetDateTime deployedAt,
            long durationSeconds
    ) {
        Deployment deployment = new Deployment();
        deployment.setMonitoredSystem(system);
        deployment.setVersion(version);
        deployment.setEnvironment(system.getEnvironment());
        deployment.setStatus(status);
        deployment.setDeployedAt(deployedAt);
        deployment.setDurationSeconds(durationSeconds);
        deployment.setCommitHash("a1b2c3d4e5f6");
        deployment.setDescription("Integration-test deployment");
        return deployment;
    }

    private TestReport testReport(
            MonitoredSystem system,
            OffsetDateTime generatedAt,
            int total,
            int passed,
            int failed,
            int skipped,
            String lineCoverage,
            String branchCoverage
    ) {
        TestReport report = new TestReport();
        report.setMonitoredSystem(system);
        report.setGeneratedAt(generatedAt);
        report.setTotalTests(total);
        report.setPassedTests(passed);
        report.setFailedTests(failed);
        report.setSkippedTests(skipped);
        report.setLineCoverage(new BigDecimal(lineCoverage));
        report.setBranchCoverage(new BigDecimal(branchCoverage));
        return report;
    }

    private Notification notification(
            User user,
            String title,
            NotificationType type,
            boolean read
    ) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage("Integration-test notification");
        notification.setType(type);
        notification.setRead(read);
        return notification;
    }
}
