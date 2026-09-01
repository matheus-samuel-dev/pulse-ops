package com.pulseops.service.monitoring;

import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.dto.common.TimeRange;
import com.pulseops.dto.monitoring.LatencyMetrics;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class LatencyMetricsService {

    private final MonitoredSystemRepository systemRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final Clock clock;

    public LatencyMetricsService(
            MonitoredSystemRepository systemRepository,
            HealthCheckRepository healthCheckRepository,
            Clock clock
    ) {
        this.systemRepository = Objects.requireNonNull(systemRepository);
        this.healthCheckRepository = Objects.requireNonNull(healthCheckRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public LatencyMetrics calculate(UUID systemId, TimeRange period) {
        if (!systemRepository.existsById(systemId)) {
            throw new ResourceNotFoundException("Monitored system", systemId);
        }
        List<HealthCheck> checks = healthCheckRepository
                .findByMonitoredSystemIdAndCheckedAtBetweenOrderByCheckedAtAsc(
                        systemId, period.start(), period.end());
        return calculateFromChecks(checks, period);
    }

    @Transactional(readOnly = true)
    public LatencyMetrics last24Hours(UUID systemId) {
        return calculate(systemId, TimeRange.last24Hours(clock));
    }

    @Transactional(readOnly = true)
    public LatencyMetrics last7Days(UUID systemId) {
        return calculate(systemId, TimeRange.last7Days(clock));
    }

    @Transactional(readOnly = true)
    public LatencyMetrics last30Days(UUID systemId) {
        return calculate(systemId, TimeRange.last30Days(clock));
    }

    public LatencyMetrics calculateFromChecks(List<HealthCheck> checks, TimeRange period) {
        Objects.requireNonNull(checks, "checks are required");
        Objects.requireNonNull(period, "period is required");

        List<Long> samples = checks.stream()
                .filter(Objects::nonNull)
                .filter(check -> check.getCheckedAt() != null)
                .filter(check -> !check.getCheckedAt().isBefore(period.start()))
                .filter(check -> !check.getCheckedAt().isAfter(period.end()))
                .filter(check -> check.getHttpStatus() != null)
                .map(HealthCheck::getResponseTimeMs)
                .sorted(Comparator.naturalOrder())
                .toList();

        if (samples.isEmpty()) {
            return new LatencyMetrics(period, 0, BigDecimal.ZERO.setScale(2), null, null, null);
        }

        long sum = samples.stream().mapToLong(Long::longValue).sum();
        BigDecimal average = BigDecimal.valueOf(sum)
                .divide(BigDecimal.valueOf(samples.size()), 2, RoundingMode.HALF_UP);
        int p95Index = Math.max(0, (int) Math.ceil(samples.size() * 0.95d) - 1);

        return new LatencyMetrics(
                period,
                samples.size(),
                average,
                samples.getFirst(),
                samples.getLast(),
                samples.get(p95Index)
        );
    }
}
