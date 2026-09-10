package com.pulseops.service.report;

import com.pulseops.domain.deployment.Deployment;
import com.pulseops.domain.deployment.DeploymentStatus;
import com.pulseops.domain.incident.Incident;
import com.pulseops.domain.incident.IncidentSeverity;
import com.pulseops.domain.incident.IncidentStatus;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.quality.QualityClassification;
import com.pulseops.domain.quality.TestReport;
import com.pulseops.domain.system.Environment;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import com.pulseops.dto.report.OperationalEventImpact;
import com.pulseops.dto.report.OperationalEventResponse;
import com.pulseops.dto.report.OperationalEventType;
import com.pulseops.dto.report.OperationalReportKpis;
import com.pulseops.dto.report.OperationalReportResponse;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.repository.DeploymentRepository;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.IncidentRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.repository.TestReportRepository;
import com.pulseops.service.quality.QualityService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalReportService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int FEED_ITEMS_PER_SOURCE = 15;
    private static final int FEED_LIMIT = 40;

    private final MonitoredSystemRepository systemRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final IncidentRepository incidentRepository;
    private final DeploymentRepository deploymentRepository;
    private final TestReportRepository testReportRepository;
    private final QualityService qualityService;
    private final Clock clock;

    public OperationalReportService(
            MonitoredSystemRepository systemRepository,
            HealthCheckRepository healthCheckRepository,
            IncidentRepository incidentRepository,
            DeploymentRepository deploymentRepository,
            TestReportRepository testReportRepository,
            QualityService qualityService,
            Clock clock
    ) {
        this.systemRepository = systemRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.incidentRepository = incidentRepository;
        this.deploymentRepository = deploymentRepository;
        this.testReportRepository = testReportRepository;
        this.qualityService = qualityService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public OperationalReportResponse generate(String periodValue, Environment environment) {
        String period = normalizePeriod(periodValue);
        OffsetDateTime end = OffsetDateTime.now(clock);
        OffsetDateTime start = end.minus(duration(period));
        List<MonitoredSystem> systems = systemRepository.findAllByActiveTrueOrderByNameAsc().stream()
                .filter(system -> environment == null || system.getEnvironment() == environment)
                .toList();

        long checks = healthCheckRepository.countForOperationalReport(start, end, environment);
        long successfulChecks = healthCheckRepository.countSuccessfulForOperationalReport(start, end, environment);
        long deployments = deploymentRepository.countForOperationalReport(start, end, environment);
        long successfulDeployments = deploymentRepository.countByStatusForOperationalReport(
                start, end, environment, DeploymentStatus.SUCCESS);
        long activeIncidents = incidentRepository.countActiveForOperationalReport(
                List.of(IncidentStatus.OPEN, IncidentStatus.INVESTIGATING), environment);
        long openedIncidents = incidentRepository.countOpenedForOperationalReport(start, end, environment);
        List<TestReport> latestReports = testReportRepository.findLatestForOperationalReport(end, environment);

        OperationalReportKpis kpis = new OperationalReportKpis(
                systems.size(),
                countStatus(systems, SystemStatus.OPERATIONAL),
                countStatus(systems, SystemStatus.DEGRADED),
                countStatus(systems, SystemStatus.DOWN),
                countStatus(systems, SystemStatus.UNKNOWN),
                checks,
                successfulChecks,
                checks - successfulChecks,
                percentage(successfulChecks, checks),
                activeIncidents,
                openedIncidents,
                deployments,
                successfulDeployments,
                percentage(successfulDeployments, deployments),
                latestReports.stream().mapToLong(TestReport::getTotalTests).sum(),
                latestReports.stream().mapToLong(TestReport::getPassedTests).sum(),
                latestReports.stream().mapToLong(TestReport::getFailedTests).sum(),
                latestReports.stream().mapToLong(TestReport::getSkippedTests).sum(),
                percentage(
                        latestReports.stream().mapToLong(TestReport::getPassedTests).sum(),
                        latestReports.stream().mapToLong(TestReport::getTotalTests).sum()),
                average(latestReports.stream().map(TestReport::getLineCoverage).toList()),
                average(latestReports.stream().map(TestReport::getBranchCoverage).toList()));

        return new OperationalReportResponse(period, environment, start, end, kpis, feed(start, end, environment));
    }

    private List<OperationalEventResponse> feed(
            OffsetDateTime start,
            OffsetDateTime end,
            Environment environment
    ) {
        PageRequest page = PageRequest.of(0, FEED_ITEMS_PER_SOURCE);
        List<OperationalEventResponse> events = new ArrayList<>();
        healthCheckRepository.findFeedForOperationalReport(start, end, environment, page)
                .forEach(check -> events.add(from(check)));
        incidentRepository.findFeedForOperationalReport(start, end, environment, page)
                .forEach(incident -> events.add(from(incident)));
        deploymentRepository.findFeedForOperationalReport(start, end, environment, page)
                .forEach(deployment -> events.add(from(deployment)));
        testReportRepository.findFeedForOperationalReport(start, end, environment, page)
                .forEach(report -> events.add(from(report)));
        return events.stream()
                .sorted(Comparator.comparing(OperationalEventResponse::occurredAt).reversed())
                .limit(FEED_LIMIT)
                .toList();
    }

    private OperationalEventResponse from(HealthCheck check) {
        MonitoredSystem system = check.getMonitoredSystem();
        String status = check.isSuccess() ? "SUCCESS" : "FAILED";
        String description = check.isSuccess()
                ? "Resposta HTTP %s em %d ms".formatted(check.getHttpStatus(), check.getResponseTimeMs())
                : check.getErrorMessage();
        OperationalEventImpact impact = check.isSuccess()
                ? OperationalEventImpact.SUCCESS
                : check.getHttpStatus() != null && check.getHttpStatus() >= 500
                        ? OperationalEventImpact.CRITICAL
                        : OperationalEventImpact.WARNING;
        return event(check.getId(), OperationalEventType.HEALTH_CHECK, check.getCheckedAt(), system,
                check.isSuccess() ? "Health check bem-sucedido" : "Falha no health check",
                description, status, impact, "Scheduler PulseOps");
    }

    private OperationalEventResponse from(Incident incident) {
        MonitoredSystem system = incident.getMonitoredSystem();
        OperationalEventImpact impact = incident.getStatus() == IncidentStatus.RESOLVED
                ? OperationalEventImpact.SUCCESS
                : incident.getSeverity() == IncidentSeverity.CRITICAL || incident.getSeverity() == IncidentSeverity.HIGH
                        ? OperationalEventImpact.CRITICAL
                        : OperationalEventImpact.WARNING;
        return event(incident.getId(), OperationalEventType.INCIDENT, incident.getStartedAt(), system,
                incident.getTitle(), incident.getDescription(), incident.getStatus().name(), impact,
                incident.isAutomatic() ? "PulseOps Automation" : "Equipe de Operações");
    }

    private OperationalEventResponse from(Deployment deployment) {
        MonitoredSystem system = deployment.getMonitoredSystem();
        OperationalEventImpact impact = switch (deployment.getStatus()) {
            case SUCCESS -> OperationalEventImpact.SUCCESS;
            case FAILED -> OperationalEventImpact.CRITICAL;
            case ROLLED_BACK -> OperationalEventImpact.WARNING;
            default -> OperationalEventImpact.INFO;
        };
        return event(deployment.getId(), OperationalEventType.DEPLOYMENT, deployment.getDeployedAt(), system,
                "Deploy " + deployment.getVersion(), deployment.getDescription(), deployment.getStatus().name(), impact,
                "GitHub Actions");
    }

    private OperationalEventResponse from(TestReport report) {
        MonitoredSystem system = report.getMonitoredSystem();
        QualityClassification classification = qualityService.analyze(report).classification();
        OperationalEventImpact impact = switch (classification) {
            case EXCELLENT, GOOD -> OperationalEventImpact.SUCCESS;
            case WARNING -> OperationalEventImpact.WARNING;
            case CRITICAL -> OperationalEventImpact.CRITICAL;
        };
        String description = "%d testes · %s%% linhas · %s%% branches".formatted(
                report.getTotalTests(), report.getLineCoverage(), report.getBranchCoverage());
        return event(report.getId(), OperationalEventType.QUALITY, report.getGeneratedAt(), system,
                "Relatório de qualidade", description, classification.name(), impact, "Pipeline de Qualidade");
    }

    private OperationalEventResponse event(
            java.util.UUID id,
            OperationalEventType type,
            OffsetDateTime occurredAt,
            MonitoredSystem system,
            String title,
            String description,
            String status,
            OperationalEventImpact impact,
            String source
    ) {
        return new OperationalEventResponse(id, type, occurredAt, system.getId(), system.getName(),
                system.getEnvironment(), title, description, status, impact, source);
    }

    private long countStatus(List<MonitoredSystem> systems, SystemStatus status) {
        return systems.stream().filter(system -> system.getStatus() == status).count();
    }

    private BigDecimal average(List<BigDecimal> values) {
        if (values.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(numerator).multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private String normalizePeriod(String value) {
        if (value == null || value.isBlank()) {
            return "7d";
        }
        return switch (value.toLowerCase()) {
            case "24h", "7d", "30d" -> value.toLowerCase();
            default -> throw new BusinessRuleException("Operational report period must be one of: 24h, 7d or 30d");
        };
    }

    private Duration duration(String period) {
        return switch (period) {
            case "24h" -> Duration.ofHours(24);
            case "7d" -> Duration.ofDays(7);
            case "30d" -> Duration.ofDays(30);
            default -> throw new IllegalArgumentException("Unsupported period: " + period);
        };
    }
}
