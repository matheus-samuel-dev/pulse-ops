package com.pulseops.service.integration;

import com.pulseops.config.IntegrationProperties;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.quality.TestReport;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.integration.IntegrationOverviewResponse;
import com.pulseops.dto.integration.IntegrationResponse;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.repository.TestReportRepository;
import com.pulseops.security.DemoModeProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class IntegrationService {
    private final MonitoredSystemRepository systems;
    private final HealthCheckRepository checks;
    private final TestReportRepository reports;
    private final IntegrationProperties properties;
    private final DemoModeProperties demo;
    private final Clock clock;

    public IntegrationService(MonitoredSystemRepository systems, HealthCheckRepository checks,
            TestReportRepository reports, IntegrationProperties properties, DemoModeProperties demo, Clock clock) {
        this.systems = systems;
        this.checks = checks;
        this.reports = reports;
        this.properties = properties;
        this.demo = demo;
        this.clock = clock;
    }

    public IntegrationOverviewResponse overview() {
        OffsetDateTime now = OffsetDateTime.now(clock);
        List<IntegrationResponse> entries = IntegrationCatalog.entries().stream().map(entry -> describe(entry, now)).toList();
        List<UUID> ids = entries.stream().filter(IntegrationResponse::configured)
                .map(IntegrationResponse::systemId).distinct().toList();
        OffsetDateTime start = now.atZoneSameInstant(properties.reportingZone()).toLocalDate()
                .atStartOfDay(properties.reportingZone()).toOffsetDateTime();
        long today = ids.isEmpty() ? 0
                : checks.countByMonitoredSystemIdInAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(ids, start, now)
                + reports.countByMonitoredSystemIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(ids, start, now);
        return new IntegrationOverviewResponse(entries, new IntegrationOverviewResponse.Summary(
                entries.stream().filter(IntegrationResponse::configured).count(),
                entries.stream().filter(entry -> entry.status().equals("ONLINE")).count(),
                entries.stream().filter(entry -> List.of("OFFLINE", "ATTENTION").contains(entry.status())).count(),
                today), demo.readOnly(), now, properties.reportingZone().getId());
    }

    public IntegrationResponse detail(String slug) {
        return describe(IntegrationCatalog.find(slug), OffsetDateTime.now(clock));
    }

    public Optional<MonitoredSystem> resolve(IntegrationCatalog entry) {
        UUID id = properties.binding(entry.slug()).systemId();
        // An explicit binding never silently falls back to another system after deletion.
        return id == null ? systems.findByNameIgnoreCase(entry.title()) : systems.findById(id);
    }

    public List<UUID> systemIds() {
        return IntegrationCatalog.entries().stream().map(this::resolve).flatMap(Optional::stream)
                .map(MonitoredSystem::getId).distinct().toList();
    }

    private IntegrationResponse describe(IntegrationCatalog entry, OffsetDateTime now) {
        MonitoredSystem system = resolve(entry).orElse(null);
        HealthCheck latest = system == null ? null : checks.findFirstByMonitoredSystemIdOrderByCheckedAtDesc(system.getId()).orElse(null);
        HealthCheck failure = system == null ? null : checks.findFirstByMonitoredSystemIdAndSuccessFalseOrderByCheckedAtDesc(system.getId()).orElse(null);
        TestReport report = system == null ? null : reports.findFirstByMonitoredSystemIdOrderByCreatedAtDesc(system.getId()).orElse(null);
        long total = system == null ? 0 : checks.countByMonitoredSystemIdAndCheckedAtBetween(system.getId(), now.minusDays(1), now);
        long success = system == null ? 0 : checks.countByMonitoredSystemIdAndSuccessTrueAndCheckedAtBetween(system.getId(), now.minusDays(1), now);
        BigDecimal health = total < 5 ? null : BigDecimal.valueOf(success).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
        String status = IntegrationStatusMapper.status(system, latest, now, properties.staleAfter());
        return new IntegrationResponse(entry.slug(), entry.title(), entry.description(), entry.type(),
                entry == IntegrationCatalog.AI_WEB_AUDITOR, system == null ? null : system.getId(),
                system != null, system != null && system.isActive(), status,
                IntegrationStatusMapper.reason(system, latest, status),
                publicOrigin(properties.binding(entry.slug()).publicUrl()),
                system == null ? null : "Endereço protegido · cadastro de Sistemas",
                system == null ? null : "Endpoint protegido · verificação pelo backend",
                latest == null ? null : latest.getCheckedAt(), report == null ? null : report.getCreatedAt(),
                report == null ? null : report.getGeneratedAt(), latest == null ? null : latest.getResponseTimeMs(),
                health, total, total - success, failure == null ? null : failure.getCheckedAt(),
                latest == null ? null : latest.getCheckedAt().plus(properties.checkCooldown()));
    }

    static String publicOrigin(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value);
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || uri.getPort() == 0 || uri.getPort() > 65535) return null;
            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
