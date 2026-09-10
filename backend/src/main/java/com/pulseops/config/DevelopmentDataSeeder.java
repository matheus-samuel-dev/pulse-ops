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
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevelopmentDataSeeder implements ApplicationRunner {

    private static final Set<String> DEMO_SYSTEM_NAMES = Set.of(
            "PlaySpace", "LogiTrack", "Gestão Financeira", "AI Web Auditor");

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
            refreshExistingDemoDataset();
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

    private void refreshExistingDemoDataset() {
        List<MonitoredSystem> demoSystems = systemRepository.findAll().stream()
                .filter(system -> DEMO_SYSTEM_NAMES.contains(system.getName()))
                .toList();
        if (demoSystems.size() != DEMO_SYSTEM_NAMES.size()) {
            return;
        }

        demoSystems.forEach(system -> system.setStatus(switch (system.getName()) {
            case "PlaySpace", "Gestão Financeira" -> SystemStatus.OPERATIONAL;
            case "LogiTrack" -> SystemStatus.DEGRADED;
            case "AI Web Auditor" -> SystemStatus.DOWN;
            default -> system.getStatus();
        }));

        Set<java.util.UUID> demoIds = demoSystems.stream().map(MonitoredSystem::getId).collect(java.util.stream.Collectors.toSet());
        List<HealthCheck> checks = healthCheckRepository.findAll().stream()
                .filter(check -> demoIds.contains(check.getMonitoredSystem().getId()))
                .toList();
        OffsetDateTime latestCheck = checks.stream().map(HealthCheck::getCheckedAt)
                .max(OffsetDateTime::compareTo).orElse(null);
        if (latestCheck == null) {
            return;
        }

        Duration shift = Duration.between(latestCheck, OffsetDateTime.now(clock));
        checks.forEach(check -> {
            check.setCheckedAt(check.getCheckedAt().plus(shift));
            check.setErrorMessage(publicDemoErrorMessage(check.getErrorMessage()));
        });
        healthCheckRepository.saveAll(checks);

        List<Incident> incidents = incidentRepository.findAll().stream()
                .filter(incident -> demoIds.contains(incident.getMonitoredSystem().getId()))
                .toList();
        Incident primaryAuditorIncident = incidents.stream()
                .filter(incident -> "AI Web Auditor".equals(incident.getMonitoredSystem().getName()))
                .filter(Incident::isAutomatic)
                .filter(incident -> incident.getStatus() != IncidentStatus.RESOLVED)
                .max(java.util.Comparator.comparing(Incident::getStartedAt))
                .orElse(null);
        incidents.forEach(incident -> {
            incident.setStartedAt(incident.getStartedAt().plus(shift));
            if (incident.getResolvedAt() != null) incident.setResolvedAt(incident.getResolvedAt().plus(shift));
            if ("Automated availability incident".equals(incident.getTitle())) {
                incident.setTitle("Instabilidade de disponibilidade detectada");
                incident.setDescription("O PulseOps detectou falhas consecutivas de health check e registrou este incidente automaticamente.");
            }
            if (incident == primaryAuditorIncident) {
                incident.setTitle("Indisponibilidade após deploy 2.4.9");
                incident.setDescription("O release 2.4.9 falhou e foi seguido por três timeouts consecutivos no worker de auditoria.");
                incident.setSeverity(IncidentSeverity.CRITICAL);
                incident.setStartedAt(OffsetDateTime.now(clock).minusMinutes(25));
            } else if (incident.isAutomatic() && incident.getStatus() != IncidentStatus.RESOLVED) {
                incident.setStatus(IncidentStatus.RESOLVED);
                incident.setResolvedAt(OffsetDateTime.now(clock));
            }
        });
        incidentRepository.saveAll(incidents);

        List<Deployment> deployments = deploymentRepository.findAll().stream()
                .filter(deployment -> demoIds.contains(deployment.getMonitoredSystem().getId()))
                .toList();
        deployments.forEach(deployment -> {
            deployment.setDeployedAt(deployment.getDeployedAt().plus(shift));
            if ("AI Web Auditor".equals(deployment.getMonitoredSystem().getName())
                    && deployment.getStatus() == DeploymentStatus.FAILED) {
                deployment.setVersion("2.4.9");
                deployment.setDeployedAt(OffsetDateTime.now(clock).minusMinutes(70));
                deployment.setDurationSeconds(142L);
                deployment.setDescription("Pipeline interrompido após regressão no worker; incidente crítico correlacionado automaticamente.");
            }
        });
        deploymentRepository.saveAll(deployments);

        List<TestReport> reports = testReportRepository.findAll().stream()
                .filter(report -> demoIds.contains(report.getMonitoredSystem().getId()))
                .toList();
        reports.forEach(report -> report.setGeneratedAt(report.getGeneratedAt().plus(shift)));
        testReportRepository.saveAll(reports);

        List<Notification> notifications = notificationRepository.findAll();
        notifications.stream().filter(notification -> "Incidente crítico aberto".equals(notification.getTitle()))
                .forEach(notification -> notification.setMessage("AI Web Auditor ficou indisponível após o deploy 2.4.9."));
        notificationRepository.saveAll(notifications);
    }

    private String publicDemoErrorMessage(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) {
            return errorMessage;
        }
        String normalized = errorMessage.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("timeout") || normalized.contains("timed out")
                || normalized.contains("time out") || normalized.contains("tempo limite")) {
            return "Tempo limite excedido durante o health check.";
        }
        if (normalized.contains("dns") || normalized.contains("resolve")) {
            return "Falha ao resolver o endereço do serviço.";
        }
        if (normalized.contains("status")) {
            return "A API retornou um status HTTP inesperado.";
        }
        return "Falha de comunicação durante o health check.";
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
                        IncidentStatus.RESOLVED, now.minusHours(18), now.minusHours(17), false,
                        "O gateway apresentou HTTP 503 durante uma janela curta e normalizou sem perda de reservas."),
                incident(logiTrack, "Latência elevada no rastreamento", IncidentSeverity.HIGH,
                        IncidentStatus.INVESTIGATING, now.minusHours(3), null, false,
                        "O p95 excedeu 800 ms em consultas de rastreamento. A equipe investiga saturação no serviço de rotas."),
                incident(finance, "Falhas intermitentes na conciliação", IncidentSeverity.MEDIUM,
                        IncidentStatus.OPEN, now.minusHours(7), null, false,
                        "Três respostas HTTP 503 foram observadas no processamento em staging; não há impacto em produção."),
                incident(auditor, "Indisponibilidade após deploy 2.4.9", IncidentSeverity.CRITICAL,
                        IncidentStatus.OPEN, now.minusMinutes(25), null, true,
                        "O release 2.4.9 falhou e foi seguido por três timeouts consecutivos no worker de auditoria."),
                incident(auditor, "Timeout no pipeline de auditoria", IncidentSeverity.HIGH,
                        IncidentStatus.RESOLVED, now.minusDays(3), now.minusDays(3).plusMinutes(42), false,
                        "Uma fila de auditorias excedeu o limite de execução e foi drenada após ajuste de concorrência.")
        ));
    }

    private Incident incident(
            MonitoredSystem system,
            String title,
            IncidentSeverity severity,
            IncidentStatus status,
            OffsetDateTime startedAt,
            OffsetDateTime resolvedAt,
            boolean automatic,
            String description
    ) {
        Incident incident = new Incident();
        incident.setMonitoredSystem(system);
        incident.setTitle(title);
        incident.setDescription(description);
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
                boolean correlatedFailure = release == 2 && systemIndex == 3;
                deployment.setVersion(correlatedFailure ? "2.4.9" : "2.%d.%d".formatted(systemIndex + 1, 8 - release));
                deployment.setEnvironment(system.getEnvironment());
                deployment.setStatus(correlatedFailure ? DeploymentStatus.FAILED : DeploymentStatus.SUCCESS);
                deployment.setDeployedAt(correlatedFailure
                        ? now.minusMinutes(70)
                        : now.minusDays(release * 3L + systemIndex).minusHours(systemIndex));
                deployment.setDurationSeconds(correlatedFailure ? 142L : 75L + release * 18L + systemIndex * 7L);
                deployment.setCommitHash("%07x".formatted(0xabc100 + systemIndex * 100 + release));
                deployment.setDescription(correlatedFailure
                        ? "Pipeline interrompido após regressão no worker; incidente crítico correlacionado automaticamente."
                        : "Release automatizado e validado pelo pipeline de entrega.");
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
                notification(admin, "Incidente crítico aberto", "AI Web Auditor ficou indisponível após o deploy 2.4.9.",
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
