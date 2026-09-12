package com.pulseops.service.integration;

import com.pulseops.domain.quality.TestReport;
import com.pulseops.dto.report.OperationalEventImpact;
import com.pulseops.repository.HealthCheckRepository;
import com.pulseops.repository.TestReportRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.pulseops.service.integration.IntegrationServiceTest.*;

@ExtendWith(MockitoExtension.class)
class IntegrationActivityServiceTest {
    @Mock IntegrationService integrations;
    @Mock HealthCheckRepository checks;
    @Mock TestReportRepository reports;

    @Test void emptyCatalogDoesNotReadUnrelatedRecords() {
        var service = new IntegrationActivityService(integrations, checks, reports, CLOCK);
        assertThat(service.events(null)).isEmpty();
        assertThat(service.events("helpdesk")).isEmpty();
        verifyNoInteractions(checks, reports);
    }

    @Test void mergesScopedEventsByReceiptTimeAndLimitsFeedWithoutLeakingErrors() {
        var system = system();
        when(integrations.resolve(IntegrationCatalog.AI_WEB_AUDITOR)).thenReturn(Optional.of(system));
        when(checks.findByMonitoredSystemIdInAndCheckedAtBetweenOrderByCheckedAtDesc(eq(List.of(system.getId())), any(), any(), any()))
                .thenReturn(IntStream.range(0, 12).mapToObj(index -> check(system, index % 2 == 0, NOW.minusMinutes(index + 1))).toList());
        TestReport report = new TestReport(); report.setId(UUID.randomUUID()); report.setMonitoredSystem(system);
        report.setGeneratedAt(NOW.minusDays(5)); report.setTotalTests(12); report.setFailedTests(2);
        ReflectionTestUtils.setField(report, "createdAt", NOW);
        when(reports.findByMonitoredSystemIdInAndCreatedAtBetweenOrderByCreatedAtDesc(eq(List.of(system.getId())), any(), any(), any())).thenReturn(List.of(report));
        var result = new IntegrationActivityService(integrations, checks, reports, CLOCK).events("ai-web-auditor");
        assertThat(result).hasSize(12);
        assertThat(result.getFirst().occurredAt()).isEqualTo(NOW);
        assertThat(result.getFirst().impact()).isEqualTo(OperationalEventImpact.WARNING);
        assertThat(result).allSatisfy(event -> assertThat(event.systemId()).isEqualTo(system.getId()));
    }

    @Test void successfulQualityReceiptUsesExistingEventContract() {
        var system = system();
        when(integrations.systemIds()).thenReturn(List.of(system.getId()));
        TestReport report = new TestReport(); report.setMonitoredSystem(system); report.setGeneratedAt(NOW);
        ReflectionTestUtils.setField(report, "createdAt", NOW);
        when(reports.findByMonitoredSystemIdInAndCreatedAtBetweenOrderByCreatedAtDesc(any(), any(), any(), any())).thenReturn(List.of(report));
        var result = new IntegrationActivityService(integrations, checks, reports, CLOCK).events(null);
        assertThat(result.getFirst().impact()).isEqualTo(OperationalEventImpact.SUCCESS);
    }
}
