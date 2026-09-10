package com.pulseops.service;

import com.pulseops.domain.deployment.Deployment;
import com.pulseops.domain.incident.Incident;
import com.pulseops.domain.incident.IncidentStatus;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.quality.TestReport;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import com.pulseops.domain.system.Environment;
import com.pulseops.dto.common.TimeRange;
import com.pulseops.dto.dashboard.DashboardSummaryResponse;
import com.pulseops.dto.dashboard.ErrorBreakdownResponse;
import com.pulseops.dto.dashboard.LatencyPointResponse;
import com.pulseops.dto.dashboard.SystemHealthResponse;
import com.pulseops.repository.DeploymentRepository;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.IncidentRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.repository.TestReportRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final MonitoredSystemRepository systemRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final IncidentRepository incidentRepository;
    private final DeploymentRepository deploymentRepository;
    private final TestReportRepository testReportRepository;
    private final Clock clock;

    public DashboardService(
            MonitoredSystemRepository systemRepository,
            HealthCheckRepository healthCheckRepository,
            IncidentRepository incidentRepository,
            DeploymentRepository deploymentRepository,
            TestReportRepository testReportRepository,
            Clock clock
    ) {
        this.systemRepository = systemRepository;
        this.healthCheckRepository = healthCheckRepository;
        this.incidentRepository = incidentRepository;
        this.deploymentRepository = deploymentRepository;
        this.testReportRepository = testReportRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(String periodValue) {
        return summary(periodValue, null);
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(String periodValue, Environment environment) {
        TimeRange period = period(periodValue);
        Duration duration = Duration.between(period.start(), period.end());
        TimeRange previous = new TimeRange(period.start().minus(duration), period.start());
        List<MonitoredSystem> systems = systemRepository.findAll().stream()
                .filter(MonitoredSystem::isActive)
                .filter(system -> environment == null || system.getEnvironment() == environment)
                .toList();
        List<HealthCheck> currentChecks = checks(period, environment);
        List<HealthCheck> previousChecks = checks(previous, environment);
        List<Incident> incidents = incidentRepository.findAll().stream()
                .filter(incident -> incident.getMonitoredSystem().isActive())
                .filter(incident -> environment == null || incident.getMonitoredSystem().getEnvironment() == environment)
                .toList();
        List<Deployment> deployments = deploymentRepository.findAll().stream()
                .filter(deployment -> deployment.getMonitoredSystem().isActive())
                .filter(deployment -> environment == null || deployment.getMonitoredSystem().getEnvironment() == environment)
                .toList();
        List<TestReport> reports = testReportRepository.findAll().stream()
                .filter(report -> report.getMonitoredSystem().isActive())
                .filter(report -> environment == null || report.getMonitoredSystem().getEnvironment() == environment)
                .toList();

        BigDecimal availability = aggregateAvailability(systems, currentChecks);
        BigDecimal previousAvailability = aggregateAvailability(systems, previousChecks);
        BigDecimal coverage = averageLatestCoverage(reports, period.end());
        BigDecimal previousCoverage = averageLatestCoverage(reports, period.start());
        long activeIncidents = incidents.stream()
                .filter(item -> item.getStatus() == IncidentStatus.OPEN || item.getStatus() == IncidentStatus.INVESTIGATING)
                .count();
        long currentIncidents = incidents.stream().filter(item -> inside(item.getStartedAt(), period)).count();
        long previousIncidents = incidents.stream().filter(item -> inside(item.getStartedAt(), previous)).count();
        long currentDeployments = deployments.stream().filter(item -> inside(item.getDeployedAt(), period)).count();
        int operational = (int) systems.stream().filter(item -> item.getStatus() == SystemStatus.OPERATIONAL).count();
        int degraded = (int) systems.stream().filter(item -> item.getStatus() == SystemStatus.DEGRADED).count();
        int down = (int) systems.stream().filter(item -> item.getStatus() == SystemStatus.DOWN).count();

        return new DashboardSummaryResponse(
                systems.size(), availability, availability.subtract(previousAvailability).setScale(2, RoundingMode.HALF_UP),
                activeIncidents, currentIncidents - previousIncidents,
                coverage, coverage.subtract(previousCoverage).setScale(2, RoundingMode.HALF_UP),
                currentDeployments, operational, degraded, down, overallHealth(down, degraded, activeIncidents),
                normalizePeriod(periodValue));
    }

    @Transactional(readOnly = true)
    public List<LatencyPointResponse> latency(String periodValue) {
        return latency(periodValue, null);
    }

    @Transactional(readOnly = true)
    public List<LatencyPointResponse> latency(String periodValue, Environment environment) {
        TimeRange period = period(periodValue);
        boolean hourly = "24h".equals(normalizePeriod(periodValue));
        Map<OffsetDateTime, List<Long>> buckets = new LinkedHashMap<>();
        checks(period, environment).stream()
                .filter(check -> check.getHttpStatus() != null)
                .forEach(check -> buckets.computeIfAbsent(bucket(check.getCheckedAt(), hourly), ignored -> new ArrayList<>())
                        .add(check.getResponseTimeMs()));
        return buckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> latencyPoint(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ErrorBreakdownResponse errors(String periodValue) {
        return errors(periodValue, null);
    }

    @Transactional(readOnly = true)
    public ErrorBreakdownResponse errors(String periodValue, Environment environment) {
        long server = 0;
        long client = 0;
        long timeout = 0;
        long other = 0;
        for (HealthCheck check : checks(period(periodValue), environment)) {
            if (check.isSuccess()) {
                continue;
            }
            if (check.getHttpStatus() != null && check.getHttpStatus() >= 500) {
                server++;
            } else if (check.getHttpStatus() != null && check.getHttpStatus() >= 400) {
                client++;
            } else if (isTimeout(check.getErrorMessage())) {
                timeout++;
            } else {
                other++;
            }
        }
        return new ErrorBreakdownResponse(server, client, timeout, other, server + client + timeout + other,
                normalizePeriod(periodValue));
    }

    @Transactional(readOnly = true)
    public List<SystemHealthResponse> health(String periodValue) {
        return health(periodValue, null);
    }

    @Transactional(readOnly = true)
    public List<SystemHealthResponse> health(String periodValue, Environment environment) {
        TimeRange period = period(periodValue);
        List<HealthCheck> allChecks = checks(period, environment);
        Map<UUID, List<HealthCheck>> bySystem = allChecks.stream()
                .collect(Collectors.groupingBy(check -> check.getMonitoredSystem().getId()));
        return systemRepository.findAll().stream()
                .filter(MonitoredSystem::isActive)
                .filter(system -> environment == null || system.getEnvironment() == environment)
                .sorted(Comparator.comparing(MonitoredSystem::getName, String.CASE_INSENSITIVE_ORDER))
                .map(system -> health(system, bySystem.getOrDefault(system.getId(), List.of())))
                .toList();
    }

    private SystemHealthResponse health(MonitoredSystem system, List<HealthCheck> checks) {
        List<HealthCheck> sorted = checks.stream().sorted(Comparator.comparing(HealthCheck::getCheckedAt)).toList();
        long successful = sorted.stream().filter(HealthCheck::isSuccess).count();
        BigDecimal uptime = percentage(successful, sorted.size());
        HealthCheck last = sorted.isEmpty() ? null : sorted.getLast();
        List<Long> sparkline = sorted.stream()
                .filter(check -> check.getHttpStatus() != null)
                .skip(Math.max(0, sorted.size() - 12L))
                .map(HealthCheck::getResponseTimeMs)
                .toList();
        return new SystemHealthResponse(
                system.getId(), system.getName(), system.getEnvironment(), system.getStatus(), uptime,
                last == null ? null : last.getResponseTimeMs(), last == null ? null : last.getCheckedAt(), sparkline);
    }

    private LatencyPointResponse latencyPoint(OffsetDateTime timestamp, List<Long> samples) {
        List<Long> sorted = samples.stream().sorted().toList();
        long sum = sorted.stream().mapToLong(Long::longValue).sum();
        BigDecimal average = BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(sorted.size()), 2, RoundingMode.HALF_UP);
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95d) - 1);
        return new LatencyPointResponse(timestamp, average, sorted.get(index), sorted.size());
    }

    private BigDecimal aggregateAvailability(List<MonitoredSystem> systems, List<HealthCheck> checks) {
        if (systems.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        Map<UUID, List<HealthCheck>> grouped = checks.stream()
                .collect(Collectors.groupingBy(check -> check.getMonitoredSystem().getId()));
        List<BigDecimal> withData = systems.stream()
                .map(system -> grouped.getOrDefault(system.getId(), List.of()))
                .filter(list -> !list.isEmpty())
                .map(list -> percentage(list.stream().filter(HealthCheck::isSuccess).count(), list.size()))
                .toList();
        if (withData.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return withData.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(withData.size()), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal averageLatestCoverage(List<TestReport> reports, OffsetDateTime before) {
        Map<UUID, TestReport> latest = reports.stream()
                .filter(report -> !report.getGeneratedAt().isAfter(before))
                .collect(Collectors.toMap(
                        report -> report.getMonitoredSystem().getId(),
                        Function.identity(),
                        (left, right) -> left.getGeneratedAt().isAfter(right.getGeneratedAt()) ? left : right));
        if (latest.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return latest.values().stream().map(TestReport::getLineCoverage).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(latest.size()), 2, RoundingMode.HALF_UP);
    }

    private List<HealthCheck> checks(TimeRange period, Environment environment) {
        return healthCheckRepository.findAllByCheckedAtBetweenOrderByCheckedAtAsc(period.start(), period.end()).stream()
                .filter(check -> check.getMonitoredSystem().isActive())
                .filter(check -> environment == null || check.getMonitoredSystem().getEnvironment() == environment)
                .toList();
    }

    private boolean isTimeout(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("timeout") || normalized.contains("timed out")
                || normalized.contains("time out") || normalized.contains("tempo limite");
    }

    private TimeRange period(String value) {
        return switch (normalizePeriod(value)) {
            case "7d" -> TimeRange.last7Days(clock);
            case "30d" -> TimeRange.last30Days(clock);
            default -> TimeRange.last24Hours(clock);
        };
    }

    private String normalizePeriod(String value) {
        if (value == null) {
            return "24h";
        }
        return switch (value.toLowerCase()) {
            case "7d", "30d" -> value.toLowerCase();
            default -> "24h";
        };
    }

    private OffsetDateTime bucket(OffsetDateTime time, boolean hourly) {
        return hourly
                ? time.truncatedTo(ChronoUnit.HOURS)
                : time.truncatedTo(ChronoUnit.DAYS);
    }

    private boolean inside(OffsetDateTime time, TimeRange period) {
        return time != null && !time.isBefore(period.start()) && !time.isAfter(period.end());
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(numerator).multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private String overallHealth(int down, int degraded, long activeIncidents) {
        if (down > 0) {
            return "CRITICAL";
        }
        if (degraded > 0 || activeIncidents > 0) {
            return "DEGRADED";
        }
        return "HEALTHY";
    }
}
