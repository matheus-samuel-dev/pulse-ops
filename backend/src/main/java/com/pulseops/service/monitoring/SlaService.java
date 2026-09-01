package com.pulseops.service.monitoring;

import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.common.TimeRange;
import com.pulseops.dto.monitoring.AvailabilityMetrics;
import com.pulseops.dto.monitoring.SlaMetrics;
import com.pulseops.dto.monitoring.SlaStatus;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.MonitoredSystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

@Service
public class SlaService {

    private static final BigDecimal AT_RISK_TOLERANCE = new BigDecimal("0.100");
    private static final int SCALE = 3;

    private final MonitoredSystemRepository systemRepository;
    private final AvailabilityService availabilityService;
    private final Clock clock;

    public SlaService(
            MonitoredSystemRepository systemRepository,
            AvailabilityService availabilityService,
            Clock clock
    ) {
        this.systemRepository = Objects.requireNonNull(systemRepository);
        this.availabilityService = Objects.requireNonNull(availabilityService);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public SlaMetrics calculate(UUID systemId, TimeRange period) {
        MonitoredSystem system = systemRepository.findById(systemId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitored system", systemId));
        AvailabilityMetrics availability = availabilityService.calculateForSystem(system, period);
        return evaluate(system.getTargetAvailability(), availability);
    }

    @Transactional(readOnly = true)
    public SlaMetrics last24Hours(UUID systemId) {
        return calculate(systemId, TimeRange.last24Hours(clock));
    }

    @Transactional(readOnly = true)
    public SlaMetrics last7Days(UUID systemId) {
        return calculate(systemId, TimeRange.last7Days(clock));
    }

    @Transactional(readOnly = true)
    public SlaMetrics last30Days(UUID systemId) {
        return calculate(systemId, TimeRange.last30Days(clock));
    }

    public SlaMetrics evaluate(BigDecimal targetAvailability, AvailabilityMetrics availability) {
        Objects.requireNonNull(targetAvailability, "targetAvailability is required");
        Objects.requireNonNull(availability, "availability is required");
        if (targetAvailability.compareTo(BigDecimal.ZERO) < 0
                || targetAvailability.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("SLA target must be between 0 and 100");
        }

        BigDecimal target = targetAvailability.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal current = availability.availabilityPercentage().setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal difference = current.subtract(target).setScale(SCALE, RoundingMode.HALF_UP);
        if (availability.totalChecks() == 0) {
            return new SlaMetrics(
                    availability.period(), current, target, false, difference,
                    SlaStatus.NO_DATA, 0);
        }

        boolean met = difference.signum() >= 0;
        SlaStatus status;
        if (met) {
            status = SlaStatus.MET;
        } else if (difference.abs().compareTo(AT_RISK_TOLERANCE) <= 0) {
            status = SlaStatus.AT_RISK;
        } else {
            status = SlaStatus.BREACHED;
        }
        return new SlaMetrics(
                availability.period(), current, target, met, difference,
                status, availability.totalChecks());
    }
}
