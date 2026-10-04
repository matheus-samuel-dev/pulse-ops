package com.pulseops.service.quality;

import com.pulseops.domain.quality.QualityClassification;
import com.pulseops.domain.quality.TestReport;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.quality.CreateTestReportCommand;
import com.pulseops.dto.quality.QualityOverviewResponse;
import com.pulseops.dto.quality.QualityReportResponse;
import com.pulseops.dto.quality.QualitySummary;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.repository.TestReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QualityService {

    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.pulseops.service.EventRecorder events;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final TestReportRepository testReportRepository;
    private final MonitoredSystemRepository systemRepository;
    private final Clock clock;

    public QualityService(
            TestReportRepository testReportRepository,
            MonitoredSystemRepository systemRepository,
            Clock clock
    ) {
        this.testReportRepository = Objects.requireNonNull(testReportRepository);
        this.systemRepository = Objects.requireNonNull(systemRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public QualitySummary createReport(UUID systemId, CreateTestReportCommand command) {
        Objects.requireNonNull(command, "command é obrigatório");
        validate(command);
        MonitoredSystem system = systemRepository.findById(systemId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitored system", systemId));

        TestReport report = new TestReport();
        report.setMonitoredSystem(system);
        report.setTotalTests(command.totalTests());
        report.setPassedTests(command.passedTests());
        report.setFailedTests(command.failedTests());
        report.setSkippedTests(command.skippedTests());
        report.setLineCoverage(normalizePercentage(command.lineCoverage()));
        report.setBranchCoverage(normalizePercentage(command.branchCoverage()));
        report.setGeneratedAt(command.generatedAt() == null
                ? OffsetDateTime.now(clock)
                : command.generatedAt());

        TestReport saved=testReportRepository.save(report);
        if(events!=null) events.recordResource(system,"QUALITY_RECEIVED","INFO","Relatório de testes recebido",saved.getTotalTests()+" testes · origem: "+saved.getSource(),"Relatório importado via API",saved.getId(),"RECEIVED");
        return analyze(saved);
    }

    @Transactional(readOnly = true)
    public QualitySummary getLatest(UUID systemId) {
        if (!systemRepository.existsById(systemId)) {
            throw new ResourceNotFoundException("Monitored system", systemId);
        }
        TestReport report = testReportRepository
                .findFirstByMonitoredSystemIdOrderByGeneratedAtDesc(systemId)
                .orElseThrow(() -> new ResourceNotFoundException("Test report for monitored system", systemId));
        return analyze(report);
    }

    @Transactional(readOnly = true)
    public QualityOverviewResponse getOverview() {
        return getOverview("all",null,null);
    }
    @Transactional(readOnly=true)
    public QualityOverviewResponse getOverview(String period,com.pulseops.domain.system.Environment environment,UUID systemId) {
        period=normalizePeriod(period);
        if(systemId!=null && !systemRepository.existsById(systemId)) throw new ResourceNotFoundException("Sistema",systemId);
        java.time.OffsetDateTime end=OffsetDateTime.now(clock);
        java.time.OffsetDateTime start=period.equals("all")?null:end.minus(periodDuration(period));
        List<MonitoredSystem> activeSystems=systemRepository.findAll().stream().filter(s->environment==null||s.getEnvironment()==environment).filter(s->systemId==null||systemId.equals(s.getId())).toList();
        var ids=activeSystems.stream().map(MonitoredSystem::getId).collect(java.util.stream.Collectors.toSet());
        Map<UUID,TestReport> latestBySystem=com.pulseops.service.OperationalReadModel.latestReports(testReportRepository.findAll().stream()
            .filter(r->ids.contains(r.getMonitoredSystem().getId())).filter(r->!r.getGeneratedAt().isAfter(end)).filter(r->start==null||!r.getGeneratedAt().isBefore(start)).toList())
            .stream().collect(Collectors.toMap(r->r.getMonitoredSystem().getId(),Function.identity()));

        List<TestReport> latestReports = activeSystems.stream()
                .map(system -> latestBySystem.get(system.getId()))
                .filter(Objects::nonNull)
                .toList();
        List<QualityReportResponse> systems = latestReports.stream()
                .map(this::toReportResponse)
                .toList();

        long totalTests = latestReports.stream().mapToLong(TestReport::getTotalTests).sum();
        long passedTests = latestReports.stream().mapToLong(TestReport::getPassedTests).sum();
        long failedTests = latestReports.stream().mapToLong(TestReport::getFailedTests).sum();
        long skippedTests = latestReports.stream().mapToLong(TestReport::getSkippedTests).sum();
        BigDecimal lineCoverage = averageCoverage(latestReports, TestReport::getLineCoverage);
        BigDecimal branchCoverage = averageCoverage(latestReports, TestReport::getBranchCoverage);
        BigDecimal passRate = percentage(passedTests, totalTests);
        BigDecimal coverageScore = coverageScore(lineCoverage, branchCoverage);

        return new QualityOverviewResponse(
                activeSystems.size(),
                latestReports.size(),
                activeSystems.size() - latestReports.size(),
                totalTests,
                passedTests,
                failedTests,
                skippedTests,
                passRate,
                lineCoverage,
                branchCoverage,
                coverageScore,
                classify(totalTests, failedTests, passRate, lineCoverage, branchCoverage),
                systems);
    }

    @Transactional(readOnly = true)
    public List<QualityReportResponse> getHistory(UUID systemId, String periodValue) {
        String period = normalizePeriod(periodValue);
        if (systemId != null && !systemRepository.existsById(systemId)) {
            throw new ResourceNotFoundException("Monitored system", systemId);
        }

        List<TestReport> reports;
        if ("all".equals(period)) {
            reports = systemId == null
                    ? testReportRepository.findAllByMonitoredSystem_ActiveTrueOrderByGeneratedAtAsc()
                    : testReportRepository.findByMonitoredSystemIdOrderByGeneratedAtAsc(systemId);
        } else {
            OffsetDateTime end = OffsetDateTime.now(clock);
            OffsetDateTime start = end.minus(periodDuration(period));
            reports = systemId == null
                    ? testReportRepository
                            .findAllByMonitoredSystem_ActiveTrueAndGeneratedAtBetweenOrderByGeneratedAtAsc(start, end)
                    : testReportRepository
                            .findByMonitoredSystemIdAndGeneratedAtBetweenOrderByGeneratedAtAsc(systemId, start, end);
        }
        return reports.stream()
                .sorted(Comparator.comparing(TestReport::getGeneratedAt))
                .map(this::toReportResponse)
                .toList();
    }

    public QualitySummary analyze(TestReport report) {
        Objects.requireNonNull(report, "report é obrigatório");
        validate(report);

        BigDecimal passRate = percentage(report.getPassedTests(), report.getTotalTests());
        BigDecimal coverageScore = coverageScore(report.getLineCoverage(), report.getBranchCoverage());
        QualityClassification classification = classify(
                report.getTotalTests(), report.getFailedTests(), passRate,
                report.getLineCoverage(), report.getBranchCoverage());

        return new QualitySummary(
                report.getId(),
                report.getTotalTests(),
                report.getPassedTests(),
                report.getFailedTests(),
                report.getSkippedTests(),
                passRate,
                report.getLineCoverage().setScale(2, RoundingMode.HALF_UP),
                report.getBranchCoverage().setScale(2, RoundingMode.HALF_UP),
                coverageScore,
                classification
        );
    }

    private QualityClassification classify(
            long totalTests,
            long failedTests,
            BigDecimal passRate,
            BigDecimal lineCoverage,
            BigDecimal branchCoverage
    ) {
        if (totalTests == 0) {
            return QualityClassification.NO_DATA;
        }
        if (failedTests == 0
                && atLeast(passRate, "98.00")
                && atLeast(lineCoverage, "90.00")
                && atLeast(branchCoverage, "85.00")) {
            return QualityClassification.EXCELLENT;
        }

        long maximumFailuresForGood = Math.max(1L, (long) Math.floor(totalTests * 0.01d));
        if (failedTests <= maximumFailuresForGood
                && atLeast(passRate, "95.00")
                && atLeast(lineCoverage, "80.00")
                && atLeast(branchCoverage, "70.00")) {
            return QualityClassification.GOOD;
        }
        if (atLeast(passRate, "80.00")
                && atLeast(lineCoverage, "60.00")
                && atLeast(branchCoverage, "50.00")) {
            return QualityClassification.WARNING;
        }
        return QualityClassification.CRITICAL;
    }

    private void validate(CreateTestReportCommand command) {
        validateCounts(
                command.totalTests(), command.passedTests(), command.failedTests(), command.skippedTests());
        validateCoverage(command.lineCoverage(), "Cobertura de linhas");
        validateCoverage(command.branchCoverage(), "Cobertura de ramificações");
        if (command.generatedAt() != null && command.generatedAt().isAfter(OffsetDateTime.now(clock))) {
            throw new BusinessRuleException("A geração do relatório não pode estar no futuro");
        }
    }

    private void validate(TestReport report) {
        validateCounts(
                report.getTotalTests(), report.getPassedTests(),
                report.getFailedTests(), report.getSkippedTests());
        validateCoverage(report.getLineCoverage(), "Cobertura de linhas");
        validateCoverage(report.getBranchCoverage(), "Cobertura de ramificações");
    }

    private void validateCounts(int total, int passed, int failed, int skipped) {
        if (total < 0 || passed < 0 || failed < 0 || skipped < 0) {
            throw new BusinessRuleException("Os contadores de testes não podem ser negativos");
        }
        long classifiedTests = (long) passed + failed + skipped;
        if (classifiedTests != total) {
            throw new BusinessRuleException(
                    "A soma de testes aprovados, reprovados e ignorados deve corresponder ao total");
        }
    }

    private void validateCoverage(BigDecimal coverage, String field) {
        if (coverage == null) {
            throw new BusinessRuleException(field + " é obrigatório");
        }
        if (coverage.compareTo(BigDecimal.ZERO) < 0 || coverage.compareTo(ONE_HUNDRED) > 0) {
            throw new BusinessRuleException(field + " deve estar entre 0 e 100");
        }
    }

    private BigDecimal normalizePercentage(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal percentage(long numerator, long denominator) {
        if (denominator == 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .multiply(ONE_HUNDRED)
                .divide(BigDecimal.valueOf(denominator), 3, RoundingMode.HALF_UP);
    }

    private QualityReportResponse toReportResponse(TestReport report) {
        QualitySummary summary = analyze(report);
        MonitoredSystem system = report.getMonitoredSystem();
        return new QualityReportResponse(
                system.getId(),
                system.getName(),
                system.getEnvironment(),
                summary.reportId(),
                report.getGeneratedAt(),
                summary.totalTests(),
                summary.passedTests(),
                summary.failedTests(),
                summary.skippedTests(),
                summary.passRate(),
                summary.lineCoverage(),
                summary.branchCoverage(),
                summary.coverageScore(),
                summary.classification(),report.getSource());
    }

    private TestReport newerReport(TestReport left, TestReport right) {
        return left.getGeneratedAt().isAfter(right.getGeneratedAt()) ? left : right;
    }

    private BigDecimal averageCoverage(
            List<TestReport> reports,
            Function<TestReport, BigDecimal> extractor
    ) {
        if (reports.isEmpty()) {
            return null;
        }
        return reports.stream()
                .map(extractor)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(reports.size()), 3, RoundingMode.HALF_UP);
    }

    private BigDecimal coverageScore(BigDecimal lineCoverage, BigDecimal branchCoverage) {
        if(lineCoverage==null||branchCoverage==null)return null;
        return lineCoverage
                .multiply(new BigDecimal("0.60"))
                .add(branchCoverage.multiply(new BigDecimal("0.40")))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizePeriod(String value) {
        if (value == null || value.isBlank()) {
            return "30d";
        }
        return switch (value.toLowerCase()) {
            case "24h", "7d", "30d", "all" -> value.toLowerCase();
            default -> throw new BusinessRuleException(
                    "O período do histórico deve ser: 24h, 7d, 30d ou all");
        };
    }

    private Duration periodDuration(String period) {
        return switch (period) {
            case "24h" -> Duration.ofHours(24);
            case "7d" -> Duration.ofDays(7);
            case "30d" -> Duration.ofDays(30);
            default -> throw new IllegalArgumentException("Period does not define a duration: " + period);
        };
    }

    private boolean atLeast(BigDecimal actual, String threshold) {
        return actual.compareTo(new BigDecimal(threshold)) >= 0;
    }
}
