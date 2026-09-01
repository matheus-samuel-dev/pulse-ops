package com.pulseops.dto.quality;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateTestReportRequest(
        @NotNull UUID systemId,
        @PositiveOrZero int totalTests,
        @PositiveOrZero int passedTests,
        @PositiveOrZero int failedTests,
        @PositiveOrZero int skippedTests,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal lineCoverage,
        @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal branchCoverage,
        OffsetDateTime generatedAt
) {
    public CreateTestReportCommand toCommand() {
        return new CreateTestReportCommand(
                totalTests, passedTests, failedTests, skippedTests, lineCoverage, branchCoverage, generatedAt);
    }
}
