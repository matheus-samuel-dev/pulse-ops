package com.pulseops.dto.system;

import com.pulseops.domain.system.Environment;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record MonitoredSystemRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @NotBlank
        @Size(max = 2048)
        @Pattern(regexp = "https?://.+", message = "baseUrl deve usar HTTP ou HTTPS")
        String baseUrl,
        @NotBlank @Size(max = 512) String healthEndpoint,
        @NotNull Environment environment,
        boolean active,
        @Min(100) @Max(599) int expectedStatusCode,
        @Min(100) @Max(60000) int timeoutMs,
        @Positive @Max(60000) long latencyThresholdMs,
        @NotNull @DecimalMin("0.000") @DecimalMax("100.000") BigDecimal targetAvailability,
        @Min(30) @Max(86400) Integer monitoringIntervalSeconds,
        boolean maintenance
) {
    public MonitoredSystemRequest(String name,String description,String baseUrl,String healthEndpoint,Environment environment,boolean active,int expectedStatusCode,int timeoutMs,long latencyThresholdMs,BigDecimal targetAvailability) {
        this(name,description,baseUrl,healthEndpoint,environment,active,expectedStatusCode,timeoutMs,latencyThresholdMs,targetAvailability,60,false);
    }
}
