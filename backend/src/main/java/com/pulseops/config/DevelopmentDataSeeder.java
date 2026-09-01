package com.pulseops.config;

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
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevelopmentDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final MonitoredSystemRepository systemRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final IncidentRepository incidentRepository;
    private final DeploymentRepository deploymentRepository;
    private final TestReportRepository testReportRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DevelopmentDataSeeder(
            UserRepository userRepository,
            MonitoredSystemRepository systemRepository,
            HealthCheckRepository healthCheckRepository,
            IncidentRepository incidentRepository,
            DeploymentRepository deploymentRepository,
            TestReportRepository testReportRepository,
            NotificationRepository notificationRepository,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.userRepository = userRepository;
        this.systemRepository = systemRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.incidentRepository = incidentRepository;
        this.deploymentRepository = deploymentRepository;
        this.testReportRepository = testReportRepository;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (systemRepository.count() > 0) {
            return;
        }
        User admin = createUser("Marina Costa", "admin@pulseops.dev", "PulseOps@2026", UserRole.ADMIN);
        createUser("Rafael Lima", "dev@pulseops.dev", "PulseOps@2026", UserRole.DEVELOPER);
        createUser("Demo Viewer", "viewer@pulseops.dev", "PulseOps@2026", UserRole.VIEWER);

        MonitoredSystem playSpace = system("PlaySpace", "Reserva inteligente de quadras e experiências esportivas.",
                Environment.PRODUCTION, SystemStatus.OPERATIONAL, 600, "99.950");
        MonitoredSystem logiTrack = system("LogiTrack", "Rastreamento logístico e gestão de entregas em tempo real.",
                Environment.PRODUCTION, SystemStatus.DEGRADED, 450, "99.900");
        MonitoredSystem finance = system("Gestão Financeira", "Consolidação financeira, conciliação e relatórios executivos.",
                Environment.STAGING, SystemStatus.OPERATIONAL, 700, "99.500");
        MonitoredSystem auditor = system("AI Web Auditor", "Auditoria automatizada de qualidade, SEO e acessibilidade.",
                Environment.DEVELOPMENT, SystemStatus.DOWN, 900, "98.500");
        List<MonitoredSystem> systems = systemRepository.saveAll(List.of(playSpace, logiTrack, finance, auditor));

        seedHealthChecks(systems);
        seedIncidents(playSpace, logiTrack, finance, auditor);
        seedDeployments(systems);
        seedQuality(systems);
        seedNotifications(admin);
    }

    private User createUser(String name, String email, String password, UserRole role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        return userRepository.save(user);
    }

    private MonitoredSystem system(
            String name,
            String description,
            Environment environment,
            SystemStatus status,
            long latencyThreshold,
            String targetAvailability
    ) {
        MonitoredSystem system = new MonitoredSystem();
        system.setName(name);
        system.setDescription(description);
        system.setBaseUrl("https://example.com");
        system.setHealthEndpoint("/");
        system.setEnvironment(environment);
        system.setStatus(status);
        system.setActive(true);
        system.setExpectedStatusCode(200);
        system.setTimeoutMs(3000);
        system.setLatencyThresholdMs(latencyThreshold);
        system.setTargetAvailability(new BigDecimal(targetAvailability));
        return system;
    }

    private void seedHealthChecks(List<MonitoredSystem> systems) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        Random random = new Random(20260827L);
        List<HealthCheck> checks = new ArrayList<>();
        for (MonitoredSystem system : systems) {
            for (int interval = 95; interval >= 0; interval--) {
                boolean failed = failed(system.getName(), interval);
                long latency = baseLatency(system.getName()) + random.nextInt(120);
                if (system.getName().equals("LogiTrack") && interval % 9 == 0) {
                    latency += 500;
                }
                HealthCheck check = new HealthCheck();
                check.setMonitoredSystem(system);
                check.setCheckedAt(now.minusMinutes(interval * 15L));
                check.setHttpStatus(failed && system.getName().equals("AI Web Auditor") ? null : failed ? 503 : 200);
                check.setResponseTimeMs(failed ? Math.max(latency, system.getTimeoutMs()) : latency);
                check.setSuccess(!failed);
                check.setErrorMessage(failed
                        ? system.getName().equals("AI Web Auditor") ? "Health check timed out" : "Unexpected HTTP status 503"
                        : null);
                checks.add(check);
            }
        }
        healthCheckRepository.saveAll(checks);
    }

    private boolean failed(String systemName, int interval) {
        return switch (systemName) {
            case "PlaySpace" -> interval == 74;
            case "LogiTrack" -> interval == 51 || interval == 52;
            case "Gestão Financeira" -> interval == 18 || interval == 37 || interval == 63;
            case "AI Web Auditor" -> interval < 4 || interval == 22 || interval == 23 || interval == 70;
            default -> false;
        };
    }

    private long baseLatency(String systemName) {
        return switch (systemName) {
            case "PlaySpace" -> 105L;
            case "LogiTrack" -> 315L;
            case "Gestão Financeira" -> 185L;
            case "AI Web Auditor" -> 560L;
            default -> 200L;
        };
    }

    private void seedIncidents(
            MonitoredSystem playSpace,
            MonitoredSystem logiTrack,
            MonitoredSystem finance,
            MonitoredSystem auditor
    ) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        incidentRepository.saveAll(List.of(
                incident(playSpace, "Oscilação no gateway de reservas", IncidentSeverity.MEDIUM,
                        IncidentStatus.RESOLVED, now.minusHours(18), now.minusHours(17), false),
                incident(logiTrack, "Latência elevada no rastreamento", IncidentSeverity.HIGH,
                        IncidentStatus.INVESTIGATING, now.minusHours(3), null, false),
                incident(finance, "Falhas intermitentes na conciliação", IncidentSeverity.MEDIUM,
                        IncidentStatus.OPEN, now.minusHours(7), null, false),
                incident(auditor, "Automated availability incident", IncidentSeverity.CRITICAL,
                        IncidentStatus.OPEN, now.minusMinutes(55), null, true),
                incident(auditor, "Timeout no pipeline de auditoria", IncidentSeverity.HIGH,
                        IncidentStatus.RESOLVED, now.minusDays(3), now.minusDays(3).plusMinutes(42), false)
        ));
    }

    private Incident incident(
            MonitoredSystem system,
            String title,
            IncidentSeverity severity,
            IncidentStatus status,
            OffsetDateTime startedAt,
            OffsetDateTime resolvedAt,
            boolean automatic
    ) {
        Incident incident = new Incident();
        incident.setMonitoredSystem(system);
        incident.setTitle(title);
        incident.setDescription("Evento demonstrativo com contexto operacional completo para a experiência PulseOps.");
        incident.setSeverity(severity);
        incident.setStatus(status);
        incident.setStartedAt(startedAt);
        incident.setResolvedAt(resolvedAt);
        incident.setAutomatic(automatic);
        return incident;
    }

    private void seedDeployments(List<MonitoredSystem> systems) {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<Deployment> deployments = new ArrayList<>();
        for (int systemIndex = 0; systemIndex < systems.size(); systemIndex++) {
            MonitoredSystem system = systems.get(systemIndex);
            for (int release = 0; release < 4; release++) {
                Deployment deployment = new Deployment();
                deployment.setMonitoredSystem(system);
                deployment.setVersion("2.%d.%d".formatted(systemIndex + 1, 8 - release));
                deployment.setEnvironment(system.getEnvironment());
                deployment.setStatus(release == 2 && systemIndex == 3
                        ? DeploymentStatus.FAILED : DeploymentStatus.SUCCESS);
                deployment.setDeployedAt(now.minusDays(release * 3L + systemIndex).minusHours(systemIndex));
                deployment.setDurationSeconds(75L + release * 18L + systemIndex * 7L);
                deployment.setCommitHash("%07x".formatted(0xabc100 + systemIndex * 100 + release));
                deployment.setDescription("Release automatizado pelo pipeline PulseOps.");
                deployments.add(deployment);
            }
        }
        deploymentRepository.saveAll(deployments);
    }

    private void seedQuality(List<MonitoredSystem> systems) {
        int[] tests = {112, 86, 74, 52};
        BigDecimal[] currentLine = decimals("94.20", "89.10", "86.50", "78.40");
        BigDecimal[] currentBranch = decimals("90.40", "83.80", "80.20", "69.60");
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<TestReport> reports = new ArrayList<>();
        for (int index = 0; index < systems.size(); index++) {
            reports.add(report(systems.get(index), tests[index] - 5, 0, 1,
                    currentLine[index].subtract(new BigDecimal("2.30")),
                    currentBranch[index].subtract(new BigDecimal("2.80")), now.minusDays(7)));
            reports.add(report(systems.get(index), tests[index], index == 3 ? 2 : 0, index == 1 ? 1 : 0,
                    currentLine[index], currentBranch[index], now.minusHours(index + 1L)));
        }
        testReportRepository.saveAll(reports);
    }

    private TestReport report(
            MonitoredSystem system,
            int total,
            int failed,
            int skipped,
            BigDecimal line,
            BigDecimal branch,
            OffsetDateTime generatedAt
    ) {
        TestReport report = new TestReport();
        report.setMonitoredSystem(system);
        report.setTotalTests(total);
        report.setFailedTests(failed);
        report.setSkippedTests(skipped);
        report.setPassedTests(total - failed - skipped);
        report.setLineCoverage(line);
        report.setBranchCoverage(branch);
        report.setGeneratedAt(generatedAt);
        return report;
    }

    private BigDecimal[] decimals(String... values) {
        BigDecimal[] result = new BigDecimal[values.length];
        for (int index = 0; index < values.length; index++) {
            result[index] = new BigDecimal(values[index]);
        }
        return result;
    }

    private void seedNotifications(User admin) {
        notificationRepository.saveAll(List.of(
                notification(admin, "Incidente crítico aberto", "AI Web Auditor excedeu o limite de falhas consecutivas.",
                        NotificationType.INCIDENT, false),
                notification(admin, "SLA em risco", "LogiTrack está 0,08% abaixo da meta configurada.",
                        NotificationType.WARNING, false),
                notification(admin, "Deploy concluído", "PlaySpace 2.1.8 foi publicado com sucesso.",
                        NotificationType.DEPLOYMENT, true),
                notification(admin, "Qualidade em alta", "A cobertura do PlaySpace avançou para 94,2%.",
                        NotificationType.QUALITY, false)
        ));
    }

    private Notification notification(User user, String title, String message, NotificationType type, boolean read) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setType(type);
        notification.setRead(read);
        return notification;
    }
}
