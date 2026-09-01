package com.pulseops.dto.quality;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateTestReportCommand(
        int totalTests,
        int passedTests,
        int failedTests,
        int skippedTests,
        BigDecimal lineCoverage,
        BigDecimal branchCoverage,
        OffsetDateTime generatedAt
) {
}
