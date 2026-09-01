package com.pulseops.service.monitoring;

import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.common.TimeRange;
import com.pulseops.dto.monitoring.AvailabilityMetrics;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class AvailabilityService {

    private static final int PERCENTAGE_SCALE = 3;

    private final MonitoredSystemRepository systemRepository;
    private final HealthCheckRepository healthCheckRepository;
    private final Clock clock;

    public AvailabilityService(
            MonitoredSystemRepository systemRepository,
            HealthCheckRepository healthCheckRepository,
            Clock clock
    ) {
        this.systemRepository = Objects.requireNonNull(systemRepository);
        this.healthCheckRepository = Objects.requireNonNull(healthCheckRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public AvailabilityMetrics calculate(UUID systemId, TimeRange period) {
        MonitoredSystem system = findSystem(systemId);
        return calculateForSystem(system, period);
    }

    @Transactional(readOnly = true)
    public AvailabilityMetrics last24Hours(UUID systemId) {
        return calculate(systemId, TimeRange.last24Hours(clock));
    }

    @Transactional(readOnly = true)
    public AvailabilityMetrics last7Days(UUID systemId) {
        return calculate(systemId, TimeRange.last7Days(clock));
    }

    @Transactional(readOnly = true)
    public AvailabilityMetrics last30Days(UUID systemId) {
        return calculate(systemId, TimeRange.last30Days(clock));
    }

    AvailabilityMetrics calculateForSystem(MonitoredSystem system, TimeRange period) {
        Objects.requireNonNull(system, "system is required");
        Objects.requireNonNull(period, "period is required");
        List<HealthCheck> checks = healthCheckRepository
                .findByMonitoredSystemIdAndCheckedAtBetweenOrderByCheckedAtAsc(
                        system.getId(), period.start(), period.end());
        return calculateFromChecks(checks, period);
    }

    public AvailabilityMetrics calculateFromChecks(List<HealthCheck> checks, TimeRange period) {
        Objects.requireNonNull(checks, "checks are required");
        Objects.requireNonNull(period, "period is required");

        List<HealthCheck> checksInPeriod = checks.stream()
                .filter(Objects::nonNull)
                .filter(check -> isInside(check, period))
                .toList();
        long successful = checksInPeriod.stream().filter(HealthCheck::isSuccess).count();
        long total = checksInPeriod.size();
        BigDecimal availability = total == 0
                ? BigDecimal.ZERO.setScale(PERCENTAGE_SCALE)
                : BigDecimal.valueOf(successful)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), PERCENTAGE_SCALE, RoundingMode.HALF_UP);

        return new AvailabilityMetrics(period, total, successful, total - successful, availability);
    }

    private boolean isInside(HealthCheck check, TimeRange period) {
        return check.getCheckedAt() != null
                && !check.getCheckedAt().isBefore(period.start())
                && !check.getCheckedAt().isAfter(period.end());
    }

    private MonitoredSystem findSystem(UUID systemId) {
        return systemRepository.findById(systemId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitored system", systemId));
    }
}
