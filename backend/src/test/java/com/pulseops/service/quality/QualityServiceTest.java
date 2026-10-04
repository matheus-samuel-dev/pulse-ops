package com.pulseops.service.quality;

import com.pulseops.domain.quality.QualityClassification;
import com.pulseops.domain.quality.TestReport;
import com.pulseops.domain.system.Environment;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.quality.CreateTestReportCommand;
import com.pulseops.dto.quality.QualityOverviewResponse;
import com.pulseops.dto.quality.QualityReportResponse;
import com.pulseops.dto.quality.QualitySummary;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.repository.TestReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("QualityService")
class QualityServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T15:00:00Z");
    private static final OffsetDateTime NOW_OFFSET = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Mock
    private TestReportRepository testReportRepository;
    @Mock
    private MonitoredSystemRepository systemRepository;

    private QualityService qualityService;

    @BeforeEach
    void setUp() {
        qualityService = new QualityService(
                testReportRepository,
                systemRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Nested
    @DisplayName("classification")
    class Classification {

        @Test
        void shouldClassifyExcellentAtAllExactThresholds() {
            QualitySummary summary = qualityService.analyze(
                    report(100, 100, 0, 0, "90.00", "85.00"));

            assertThat(summary.passRate()).isEqualByComparingTo("100.00");
            assertThat(summary.coverageScore()).isEqualByComparingTo("88.00");
            assertThat(summary.classification()).isEqualTo(QualityClassification.EXCELLENT);
        }

        @Test
        void shouldClassifyGoodWithAllowedFailureAndExactCoverageThresholds() {
            QualitySummary summary = qualityService.analyze(
                    report(100, 99, 1, 0, "80.00", "70.00"));

            assertThat(summary.passRate()).isEqualByComparingTo("99.00");
            assertThat(summary.classification()).isEqualTo(QualityClassification.GOOD);
        }

        @Test
        void shouldClassifyWarningAtExactMinimumThresholds() {
            QualitySummary summary = qualityService.analyze(
                    report(100, 80, 20, 0, "60.00", "50.00"));

            assertThat(summary.passRate()).isEqualByComparingTo("80.00");
            assertThat(summary.classification()).isEqualTo(QualityClassification.WARNING);
        }

        @Test
        void shouldClassifyCriticalWhenBelowWarningThresholdOrWithoutTests() {
            QualitySummary belowThreshold = qualityService.analyze(
                    report(100, 79, 21, 0, "95.00", "90.00"));
            QualitySummary withoutTests = qualityService.analyze(
                    report(0, 0, 0, 0, "100.00", "100.00"));

            assertThat(belowThreshold.classification()).isEqualTo(QualityClassification.CRITICAL);
            assertThat(withoutTests.classification()).isEqualTo(QualityClassification.NO_DATA);
            assertThat(withoutTests.passRate()).isNull();
        }

        @Test
        void shouldCalculateWeightedCoverageAndRoundedPassRate() {
            QualitySummary summary = qualityService.analyze(
                    report(3, 2, 1, 0, "92.00", "88.00"));

            assertThat(summary.passRate()).isEqualByComparingTo("66.667");
            assertThat(summary.coverageScore()).isEqualByComparingTo("90.40");
        }
    }

    @Nested
    @DisplayName("report creation")
    class ReportCreation {

        @Test
        void shouldValidatePersistAndAnalyzeReport() {
            UUID systemId = UUID.randomUUID();
            UUID reportId = UUID.randomUUID();
            MonitoredSystem system = new MonitoredSystem();
            system.setId(systemId);
            when(systemRepository.findById(systemId)).thenReturn(Optional.of(system));
            when(testReportRepository.save(any(TestReport.class))).thenAnswer(invocation -> {
                TestReport saved = invocation.getArgument(0);
                saved.setId(reportId);
                return saved;
            });
            CreateTestReportCommand command = new CreateTestReportCommand(
                    324, 323, 1, 0,
                    new BigDecimal("92.456"), new BigDecimal("87.994"), null);

            QualitySummary summary = qualityService.createReport(systemId, command);

            ArgumentCaptor<TestReport> captor = ArgumentCaptor.forClass(TestReport.class);
            verify(testReportRepository).save(captor.capture());
            TestReport saved = captor.getValue();
            assertThat(saved.getMonitoredSystem()).isSameAs(system);
            assertThat(saved.getGeneratedAt()).isEqualTo(NOW_OFFSET);
            assertThat(saved.getLineCoverage()).isEqualByComparingTo("92.46");
            assertThat(saved.getBranchCoverage()).isEqualByComparingTo("87.99");
            assertThat(summary.reportId()).isEqualTo(reportId);
            assertThat(summary.totalTests()).isEqualTo(324);
            assertThat(summary.coverageScore()).isEqualByComparingTo("90.67");
        }

        @Test
        void shouldRejectInconsistentOrNegativeCountersBeforeDatabaseAccess() {
            assertThatThrownBy(() -> qualityService.createReport(
                    UUID.randomUUID(), command(10, 8, 1, 0, "90", "80", NOW_OFFSET)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("soma");
            assertThatThrownBy(() -> qualityService.createReport(
                    UUID.randomUUID(), command(-1, -1, 0, 0, "90", "80", NOW_OFFSET)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("negativos");

            verifyNoInteractions(systemRepository, testReportRepository);
        }

        @Test
        void shouldRejectMissingOrOutOfRangeCoverageAndFutureReport() {
            assertThatThrownBy(() -> qualityService.createReport(
                    UUID.randomUUID(), new CreateTestReportCommand(
                            1, 1, 0, 0, null, BigDecimal.TEN, NOW_OFFSET)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Cobertura de linhas");
            assertThatThrownBy(() -> qualityService.createReport(
                    UUID.randomUUID(), command(1, 1, 0, 0, "101", "80", NOW_OFFSET)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("entre 0 e 100");
            assertThatThrownBy(() -> qualityService.createReport(
                    UUID.randomUUID(), command(1, 1, 0, 0, "90", "80", NOW_OFFSET.plusNanos(1))))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("futuro");

            verify(testReportRepository, never()).save(any());
            verifyNoInteractions(systemRepository);
        }

        @Test
        void shouldNotPersistValidReportForUnknownSystem() {
            UUID systemId = UUID.randomUUID();
            when(systemRepository.findById(systemId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> qualityService.createReport(
                    systemId, command(1, 1, 0, 0, "90", "80", NOW_OFFSET)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(systemId.toString());

            verify(testReportRepository, never()).save(any());
        }
    }

    @Test
    void shouldReturnLatestReportForExistingSystem() {
        UUID systemId = UUID.randomUUID();
        TestReport report = report(10, 10, 0, 0, "95", "90");
        when(systemRepository.existsById(systemId)).thenReturn(true);
        when(testReportRepository.findFirstByMonitoredSystemIdOrderByGeneratedAtDesc(systemId))
                .thenReturn(Optional.of(report));

        QualitySummary result = qualityService.getLatest(systemId);

        assertThat(result.reportId()).isEqualTo(report.getId());
        assertThat(result.classification()).isEqualTo(QualityClassification.EXCELLENT);
    }

    @Test
    void shouldDistinguishUnknownSystemFromMissingReport() {
        UUID unknownSystem = UUID.randomUUID();
        UUID systemWithoutReport = UUID.randomUUID();
        when(systemRepository.existsById(unknownSystem)).thenReturn(false);
        when(systemRepository.existsById(systemWithoutReport)).thenReturn(true);
        when(testReportRepository.findFirstByMonitoredSystemIdOrderByGeneratedAtDesc(systemWithoutReport))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> qualityService.getLatest(unknownSystem))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Sistema");
        assertThatThrownBy(() -> qualityService.getLatest(systemWithoutReport))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Relatório de testes");

        verify(testReportRepository, never())
                .findFirstByMonitoredSystemIdOrderByGeneratedAtDesc(unknownSystem);
    }

    @Nested
    @DisplayName("quality overview")
    class QualityOverview {

        @Test
        void shouldAggregateOnlyTheLatestReportOfEveryActiveSystem() {
            MonitoredSystem playSpace = system("PlaySpace", true);
            MonitoredSystem logiTrack = system("LogiTrack", true);
            MonitoredSystem withoutReport = system("AI Web Auditor", true);
            TestReport oldPlaySpace = report(
                    playSpace, NOW_OFFSET.minusDays(1), 50, 50, 0, 0, "80", "70");
            TestReport latestPlaySpace = report(
                    playSpace, NOW_OFFSET.minusHours(1), 100, 100, 0, 0, "92", "88");
            TestReport latestLogiTrack = report(
                    logiTrack, NOW_OFFSET.minusHours(2), 200, 190, 5, 5, "82", "72");
            when(systemRepository.findAll())
                    .thenReturn(List.of(withoutReport, logiTrack, playSpace));
            when(testReportRepository.findAll())
                    .thenReturn(List.of(oldPlaySpace, latestLogiTrack, latestPlaySpace));

            QualityOverviewResponse overview = qualityService.getOverview();

            assertThat(overview.monitoredSystems()).isEqualTo(3);
            assertThat(overview.systemsWithReports()).isEqualTo(2);
            assertThat(overview.systemsWithoutReports()).isEqualTo(1);
            assertThat(overview.totalTests()).isEqualTo(300);
            assertThat(overview.passedTests()).isEqualTo(290);
            assertThat(overview.failedTests()).isEqualTo(5);
            assertThat(overview.skippedTests()).isEqualTo(5);
            assertThat(overview.passRate()).isEqualByComparingTo("96.667");
            assertThat(overview.averageLineCoverage()).isEqualByComparingTo("87.00");
            assertThat(overview.averageBranchCoverage()).isEqualByComparingTo("80.00");
            assertThat(overview.averageCoverageScore()).isEqualByComparingTo("84.20");
            assertThat(overview.classification()).isEqualTo(QualityClassification.WARNING);
            assertThat(overview.systems())
                    .extracting(QualityReportResponse::systemName)
                    .containsExactly("LogiTrack", "PlaySpace");
            assertThat(overview.systems().get(1).reportId()).isEqualTo(latestPlaySpace.getId());
        }

        @Test
        void shouldReturnExplicitEmptyOverviewWhenActiveSystemsHaveNoReports() {
            when(systemRepository.findAll())
                    .thenReturn(List.of(system("Gestão Financeira", true)));
            when(testReportRepository.findAll())
                    .thenReturn(List.of());

            QualityOverviewResponse overview = qualityService.getOverview();

            assertThat(overview.monitoredSystems()).isOne();
            assertThat(overview.systemsWithReports()).isZero();
            assertThat(overview.systemsWithoutReports()).isOne();
            assertThat(overview.totalTests()).isZero();
            assertThat(overview.passRate()).isNull();
            assertThat(overview.averageLineCoverage()).isNull();
            assertThat(overview.classification()).isEqualTo(QualityClassification.NO_DATA);
            assertThat(overview.systems()).isEmpty();
        }
    }

    @Nested
    @DisplayName("quality history")
    class QualityHistory {

        @Test
        void shouldReturnActiveSystemsHistoryForRequestedRangeInChronologicalOrder() {
            MonitoredSystem system = system("PlaySpace", true);
            OffsetDateTime start = NOW_OFFSET.minusDays(7);
            TestReport newest = report(
                    system, NOW_OFFSET.minusHours(1), 12, 12, 0, 0, "94", "90");
            TestReport oldest = report(
                    system, NOW_OFFSET.minusDays(6), 10, 9, 1, 0, "82", "75");
            when(testReportRepository
                    .findAllByMonitoredSystem_ActiveTrueAndGeneratedAtBetweenOrderByGeneratedAtAsc(
                            start, NOW_OFFSET))
                    .thenReturn(List.of(newest, oldest));

            List<QualityReportResponse> history = qualityService.getHistory(null, "7D");

            assertThat(history)
                    .extracting(QualityReportResponse::generatedAt)
                    .containsExactly(oldest.getGeneratedAt(), newest.getGeneratedAt());
            assertThat(history.getFirst().systemId()).isEqualTo(system.getId());
            assertThat(history.getFirst().passRate()).isEqualByComparingTo("90.00");
            verify(testReportRepository)
                    .findAllByMonitoredSystem_ActiveTrueAndGeneratedAtBetweenOrderByGeneratedAtAsc(
                            start, NOW_OFFSET);
        }

        @Test
        void shouldReturnCompleteHistoryForOneExistingSystem() {
            MonitoredSystem system = system("LogiTrack", false);
            TestReport report = report(
                    system, NOW_OFFSET.minusDays(60), 100, 100, 0, 0, "95", "90");
            when(systemRepository.existsById(system.getId())).thenReturn(true);
            when(testReportRepository.findByMonitoredSystemIdOrderByGeneratedAtAsc(system.getId()))
                    .thenReturn(List.of(report));

            List<QualityReportResponse> history = qualityService.getHistory(system.getId(), "all");

            assertThat(history).singleElement().satisfies(point -> {
                assertThat(point.systemName()).isEqualTo("LogiTrack");
                assertThat(point.classification()).isEqualTo(QualityClassification.EXCELLENT);
            });
        }

        @Test
        void shouldRejectUnknownSystemAndUnsupportedPeriodBeforeQueryingReports() {
            UUID unknownSystem = UUID.randomUUID();
            when(systemRepository.existsById(unknownSystem)).thenReturn(false);

            assertThatThrownBy(() -> qualityService.getHistory(unknownSystem, "24h"))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(unknownSystem.toString());
            assertThatThrownBy(() -> qualityService.getHistory(null, "90d"))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("24h, 7d, 30d ou all");

            verifyNoInteractions(testReportRepository);
        }
    }

    private CreateTestReportCommand command(
            int total,
            int passed,
            int failed,
            int skipped,
            String line,
            String branch,
            OffsetDateTime generatedAt
    ) {
        return new CreateTestReportCommand(
                total, passed, failed, skipped,
                new BigDecimal(line), new BigDecimal(branch), generatedAt);
    }

    private TestReport report(
            int total,
            int passed,
            int failed,
            int skipped,
            String line,
            String branch
    ) {
        TestReport report = new TestReport();
        report.setId(UUID.randomUUID());
        report.setTotalTests(total);
        report.setPassedTests(passed);
        report.setFailedTests(failed);
        report.setSkippedTests(skipped);
        report.setLineCoverage(new BigDecimal(line));
        report.setBranchCoverage(new BigDecimal(branch));
        report.setGeneratedAt(NOW_OFFSET);
        return report;
    }

    private MonitoredSystem system(String name, boolean active) {
        MonitoredSystem system = new MonitoredSystem();
        system.setId(UUID.randomUUID());
        system.setName(name);
        system.setEnvironment(Environment.PRODUCTION);
        system.setActive(active);
        return system;
    }

    private TestReport report(
            MonitoredSystem system,
            OffsetDateTime generatedAt,
            int total,
            int passed,
            int failed,
            int skipped,
            String line,
            String branch
    ) {
        TestReport report = report(total, passed, failed, skipped, line, branch);
        report.setMonitoredSystem(system);
        report.setGeneratedAt(generatedAt);
        return report;
    }
}
