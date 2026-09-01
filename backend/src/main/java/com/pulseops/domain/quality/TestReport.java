package com.pulseops.domain.quality;

import com.pulseops.domain.common.AuditableEntity;
import com.pulseops.domain.system.MonitoredSystem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "test_reports",
        indexes = @Index(name = "idx_test_reports_system_generated", columnList = "monitored_system_id,generated_at")
)
public class TestReport extends AuditableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monitored_system_id", nullable = false)
    private MonitoredSystem monitoredSystem;

    @PositiveOrZero
    @Column(name = "total_tests", nullable = false)
    private int totalTests;

    @PositiveOrZero
    @Column(name = "passed_tests", nullable = false)
    private int passedTests;

    @PositiveOrZero
    @Column(name = "failed_tests", nullable = false)
    private int failedTests;

    @PositiveOrZero
    @Column(name = "skipped_tests", nullable = false)
    private int skippedTests;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Column(name = "line_coverage", nullable = false, precision = 5, scale = 2)
    private BigDecimal lineCoverage = BigDecimal.ZERO;

    @NotNull
    @DecimalMin("0.00")
    @DecimalMax("100.00")
    @Column(name = "branch_coverage", nullable = false, precision = 5, scale = 2)
    private BigDecimal branchCoverage = BigDecimal.ZERO;

    @NotNull
    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    public TestReport() {
    }

    public MonitoredSystem getMonitoredSystem() {
        return monitoredSystem;
    }

    public void setMonitoredSystem(MonitoredSystem monitoredSystem) {
        this.monitoredSystem = monitoredSystem;
    }

    public int getTotalTests() {
        return totalTests;
    }

    public void setTotalTests(int totalTests) {
        this.totalTests = totalTests;
    }

    public int getPassedTests() {
        return passedTests;
    }

    public void setPassedTests(int passedTests) {
        this.passedTests = passedTests;
    }

    public int getFailedTests() {
        return failedTests;
    }

    public void setFailedTests(int failedTests) {
        this.failedTests = failedTests;
    }

    public int getSkippedTests() {
        return skippedTests;
    }

    public void setSkippedTests(int skippedTests) {
        this.skippedTests = skippedTests;
    }

    public BigDecimal getLineCoverage() {
        return lineCoverage;
    }

    public void setLineCoverage(BigDecimal lineCoverage) {
        this.lineCoverage = lineCoverage;
    }

    public BigDecimal getBranchCoverage() {
        return branchCoverage;
    }

    public void setBranchCoverage(BigDecimal branchCoverage) {
        this.branchCoverage = branchCoverage;
    }

    public OffsetDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(OffsetDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
