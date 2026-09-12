package com.pulseops.service.integration;

import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.quality.TestReport;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.report.OperationalEventImpact;
import com.pulseops.dto.report.OperationalEventResponse;
import com.pulseops.dto.report.OperationalEventType;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.TestReportRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** A bounded projection of the existing operational records; no parallel event store. */
@Service
public class IntegrationActivityService {
    private final IntegrationService integrations;
    private final HealthCheckRepository checks;
    private final TestReportRepository reports;
    private final Clock clock;

    public IntegrationActivityService(IntegrationService integrations, HealthCheckRepository checks,
            TestReportRepository reports, Clock clock) {
        this.integrations = integrations;
        this.checks = checks;
        this.reports = reports;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<OperationalEventResponse> events(String slug) {
        List<UUID> ids = slug == null ? integrations.systemIds()
                : integrations.resolve(IntegrationCatalog.find(slug)).map(system -> List.of(system.getId())).orElse(List.of());
        if (ids.isEmpty()) return List.of();
        OffsetDateTime end = OffsetDateTime.now(clock);
        OffsetDateTime start = end.minusDays(30);
        PageRequest page = PageRequest.of(0, 12);
        List<OperationalEventResponse> events = new ArrayList<>();
        checks.findByMonitoredSystemIdInAndCheckedAtBetweenOrderByCheckedAtDesc(ids, start, end, page)
                .forEach(check -> events.add(fromCheck(check)));
        reports.findByMonitoredSystemIdInAndCreatedAtBetweenOrderByCreatedAtDesc(ids, start, end, page)
                .forEach(report -> events.add(fromReport(report)));
        return events.stream().sorted(Comparator.comparing(OperationalEventResponse::occurredAt).reversed()).limit(12).toList();
    }

    private OperationalEventResponse fromCheck(HealthCheck check) {
        return event(check.getId(), OperationalEventType.HEALTH_CHECK, check.getCheckedAt(), check.getMonitoredSystem(),
                check.isSuccess() ? "Conexão verificada" : "Falha de comunicação",
                IntegrationStatusMapper.checkMessage(check), check.isSuccess() ? "SUCCESS" : "FAILED",
                check.isSuccess() ? OperationalEventImpact.SUCCESS : OperationalEventImpact.CRITICAL);
    }

    private OperationalEventResponse fromReport(TestReport report) {
        return event(report.getId(), OperationalEventType.QUALITY, report.getCreatedAt(), report.getMonitoredSystem(),
                "Relatório de qualidade recebido", "%d testes · %d falhas".formatted(report.getTotalTests(), report.getFailedTests()),
                report.getFailedTests() == 0 ? "SUCCESS" : "WARNING",
                report.getFailedTests() == 0 ? OperationalEventImpact.SUCCESS : OperationalEventImpact.WARNING);
    }

    private OperationalEventResponse event(UUID id, OperationalEventType type, OffsetDateTime time,
            MonitoredSystem system, String title, String description, String status, OperationalEventImpact impact) {
        return new OperationalEventResponse(id, type, time, system.getId(), system.getName(), system.getEnvironment(),
                title, description, status, impact, "PulseOps · Integrações");
    }
}
