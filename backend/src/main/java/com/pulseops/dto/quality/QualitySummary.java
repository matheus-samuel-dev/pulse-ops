package com.pulseops.dto.quality;

import com.pulseops.domain.quality.QualityClassification;

import java.math.BigDecimal;
import java.util.UUID;

public record QualitySummary(
        UUID reportId,
        int totalTests,
        int passedTests,
        int failedTests,
        int skippedTests,
        BigDecimal passRate,
        BigDecimal lineCoverage,
        BigDecimal branchCoverage,
        BigDecimal coverageScore,
        QualityClassification classification
) {
}
