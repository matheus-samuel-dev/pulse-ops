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
    private final OperationalReadModel readModel;

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
        this.readModel=new OperationalReadModel(systemRepository,healthCheckRepository,incidentRepository,deploymentRepository,testReportRepository,clock);
    }

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public com.pulseops.dto.dashboard.DashboardDataResponse snapshot(String period,Environment environment) {
        Clock snapshotClock=Clock.fixed(clock.instant(),clock.getZone());
        DashboardService stable=new DashboardService(systemRepository,healthCheckRepository,incidentRepository,deploymentRepository,testReportRepository,snapshotClock);
        return new com.pulseops.dto.dashboard.DashboardDataResponse(stable.summary(period,environment),stable.latency(period,environment),stable.errors(period,environment),stable.health(period,environment),OffsetDateTime.now(snapshotClock));
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
        var current=readModel.read(period,environment,null);
        var before=readModel.read(previous,environment,null);
        List<MonitoredSystem> systems=current.systems();
        BigDecimal availability=current.averageAvailability();
        BigDecimal previousAvailability=before.averageAvailability();
        BigDecimal coverage=current.lineCoverage();
        BigDecimal previousCoverage=before.lineCoverage();
        long activeIncidents=current.activeIncidents();
        long currentIncidents=current.incidents().size();
        long previousIncidents=before.incidents().size();
        long currentDeployments=current.deployments().size();
        int operational=(int)current.status(SystemStatus.OPERATIONAL);
        int degraded=(int)current.status(SystemStatus.DEGRADED);
        int down=(int)current.status(SystemStatus.DOWN);

        return new DashboardSummaryResponse(
                systems.size(), availability, difference(availability, previousAvailability),
                activeIncidents, currentIncidents - previousIncidents,
                coverage, difference(coverage, previousCoverage),
                currentDeployments, operational, degraded, down, systems.stream().noneMatch(MonitoredSystem::isActive) || systems.stream().filter(MonitoredSystem::isActive).allMatch(item -> item.getStatus() == SystemStatus.UNKNOWN || item.getStatus()==SystemStatus.MAINTENANCE) ? "UNKNOWN" : overallHealth(down, degraded+(int)current.status(SystemStatus.CONFIGURATION_REQUIRED), activeIncidents),
                normalizePeriod(periodValue),(int)current.status(SystemStatus.CONFIGURATION_REQUIRED),down+degraded+(int)current.status(SystemStatus.CONFIGURATION_REQUIRED));
    }

    @Transactional(readOnly = true)
    public List<LatencyPointResponse> latency(String periodValue) {
        return latency(periodValue, null);
    }

    @Transactional(readOnly = true)
    public List<LatencyPointResponse> latency(String periodValue, Environment environment) {
        return latency(periodValue,environment,null);
    }
    @Transactional(readOnly=true)
    public List<LatencyPointResponse> latency(String periodValue,Environment environment,UUID systemId) {
        TimeRange period = period(periodValue);
        boolean hourly = "24h".equals(normalizePeriod(periodValue));
        Map<OffsetDateTime, List<Long>> buckets = new LinkedHashMap<>();
        readModel.read(period,environment,systemId).checks().stream()
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
                .filter(system -> environment == null || system.getEnvironment() == environment)
                .sorted(Comparator.comparing(MonitoredSystem::getName, String.CASE_INSENSITIVE_ORDER))
                .map(system -> health(system, bySystem.getOrDefault(system.getId(), List.of())))
                .toList();
    }

    private SystemHealthResponse health(MonitoredSystem system, List<HealthCheck> checks) {
        List<HealthCheck> sorted = checks.stream().sorted(Comparator.comparing(HealthCheck::getCheckedAt)).toList();
        long successful = sorted.stream().filter(HealthCheck::isSuccess).count();
        BigDecimal uptime = OperationalReadModel.availability(sorted);
        HealthCheck last = healthCheckRepository.findFirstByMonitoredSystemIdOrderByCheckedAtDesc(system.getId()).orElse(sorted.isEmpty() ? null : sorted.getLast());
        List<Long> sparkline = sorted.stream()
                .filter(check -> check.getHttpStatus() != null)
                .skip(Math.max(0, sorted.stream().filter(check -> check.getHttpStatus()!=null).count() - 12L))
                .map(HealthCheck::getResponseTimeMs)
                .toList();
        return new SystemHealthResponse(
                system.getId(), system.getName(), system.getEnvironment(), system.getStatus(), uptime,
                last == null || last.getHttpStatus() == null ? null : last.getResponseTimeMs(), last == null ? null : last.getCheckedAt(), sparkline, system.getStatusReason(), system.getStatusChangedAt(),system.isActive(),sorted.size());
    }

    private LatencyPointResponse latencyPoint(OffsetDateTime timestamp, List<Long> samples) {
        List<Long> sorted = samples.stream().sorted().toList();
        long sum = sorted.stream().mapToLong(Long::longValue).sum();
        BigDecimal average = BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(sorted.size()), 2, RoundingMode.HALF_UP);
        int index = Math.max(0, (int) Math.ceil(sorted.size() * 0.95d) - 1);
        return new LatencyPointResponse(timestamp, average, sorted.get(index), sorted.size());
    }

    private List<HealthCheck> checks(TimeRange period, Environment environment) {
        return healthCheckRepository.findAllByCheckedAtBetweenOrderByCheckedAtAsc(period.start(), period.end()).stream()
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

    private BigDecimal difference(BigDecimal current, BigDecimal previous) {
        return current == null || previous == null ? null : current.subtract(previous).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
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
