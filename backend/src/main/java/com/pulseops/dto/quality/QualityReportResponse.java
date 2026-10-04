package com.pulseops.dto.quality;

import com.pulseops.domain.quality.QualityClassification;
import com.pulseops.domain.system.Environment;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A quality report enriched with the monitored-system identity required by
 * product views and charts.
 */
public record QualityReportResponse(
        UUID systemId,
        String systemName,
        Environment environment,
        UUID reportId,
        OffsetDateTime generatedAt,
        int totalTests,
        int passedTests,
        int failedTests,
        int skippedTests,
        BigDecimal passRate,
        BigDecimal lineCoverage,
        BigDecimal branchCoverage,
        BigDecimal coverageScore,
        QualityClassification classification,
        String source
) {
}
