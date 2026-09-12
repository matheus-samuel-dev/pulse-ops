package com.pulseops.service.integration;

import com.pulseops.config.IntegrationProperties;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.integration.IntegrationCheckResponse;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.service.monitoring.MonitoringService;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IntegrationCheckService {
    private final IntegrationService integrations;
    private final MonitoringService monitoring;
    private final HealthCheckRepository checks;
    private final IntegrationProperties properties;
    private final Clock clock;
    private final Map<IntegrationCatalog, Object> locks = new EnumMap<>(IntegrationCatalog.class);

    public IntegrationCheckService(IntegrationService integrations, MonitoringService monitoring,
            HealthCheckRepository checks, IntegrationProperties properties, Clock clock) {
        this.integrations = integrations;
        this.monitoring = monitoring;
        this.checks = checks;
        this.properties = properties;
        this.clock = clock;
        IntegrationCatalog.entries().forEach(entry -> locks.put(entry, new Object()));
    }

    public IntegrationCheckResponse check(String slug) {
        IntegrationCatalog entry = IntegrationCatalog.find(slug);
        synchronized (locks.get(entry)) {
            MonitoredSystem system = integrations.resolve(entry)
                    .orElseThrow(() -> new BusinessRuleException("Integração não configurada"));
            if (!system.isActive()) throw new BusinessRuleException("O monitoramento desta integração está pausado");
            OffsetDateTime now = OffsetDateTime.now(clock);
            HealthCheck latest = checks.findFirstByMonitoredSystemIdOrderByCheckedAtDesc(system.getId()).orElse(null);
            boolean cached = latest != null && latest.getCheckedAt().plus(properties.checkCooldown()).isAfter(now);
            HealthCheck result = cached ? latest : monitoring.checkSystem(system.getId());
            // A repository check may contain a detached lazy association. Use the resolved system,
            // refreshing its persisted status only after a newly recorded probe.
            MonitoredSystem statusSystem = cached ? system : integrations.resolve(entry).orElse(system);
            return new IntegrationCheckResponse(slug,
                    IntegrationStatusMapper.status(statusSystem, result, OffsetDateTime.now(clock), properties.staleAfter()),
                    result.getResponseTimeMs(), result.getCheckedAt(), IntegrationStatusMapper.checkMessage(result),
                    cached, result.getCheckedAt().plus(properties.checkCooldown()));
        }
    }
}
