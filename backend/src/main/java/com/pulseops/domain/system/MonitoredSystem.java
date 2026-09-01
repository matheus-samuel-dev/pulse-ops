package com.pulseops.domain.system;

import com.pulseops.domain.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

@Entity
@Table(
        name = "monitored_systems",
        indexes = {
                @Index(name = "idx_monitored_systems_status", columnList = "status"),
                @Index(name = "idx_monitored_systems_active_environment", columnList = "active,environment")
        },
        uniqueConstraints = @UniqueConstraint(name = "uk_monitored_systems_name", columnNames = "name")
)
public class MonitoredSystem extends AuditableEntity {

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String name;

    @Size(max = 1000)
    @Column(length = 1000)
    private String description;

    @NotBlank
    @Size(max = 2048)
    @Column(name = "base_url", nullable = false, length = 2048)
    private String baseUrl;

    @NotBlank
    @Size(max = 512)
    @Column(name = "health_endpoint", nullable = false, length = 512)
    private String healthEndpoint = "/actuator/health";

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Environment environment = Environment.PRODUCTION;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SystemStatus status = SystemStatus.UNKNOWN;

    @Column(nullable = false)
    private boolean active = true;

    @Min(100)
    @Max(599)
    @Column(name = "expected_status_code", nullable = false)
    private int expectedStatusCode = 200;

    @Positive
    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs = 5000;

    @Positive
    @Column(name = "latency_threshold_ms", nullable = false)
    private long latencyThresholdMs = 1000L;

    @NotNull
    @DecimalMin("0.000")
    @DecimalMax("100.000")
    @Column(name = "target_availability", nullable = false, precision = 6, scale = 3)
    private BigDecimal targetAvailability = new BigDecimal("99.900");

    public MonitoredSystem() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getHealthEndpoint() {
        return healthEndpoint;
    }

    public void setHealthEndpoint(String healthEndpoint) {
        this.healthEndpoint = healthEndpoint;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public SystemStatus getStatus() {
        return status;
    }

    public void setStatus(SystemStatus status) {
        this.status = status;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getExpectedStatusCode() {
        return expectedStatusCode;
    }

    public void setExpectedStatusCode(int expectedStatusCode) {
        this.expectedStatusCode = expectedStatusCode;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public long getLatencyThresholdMs() {
        return latencyThresholdMs;
    }

    public void setLatencyThresholdMs(long latencyThresholdMs) {
        this.latencyThresholdMs = latencyThresholdMs;
    }

    public BigDecimal getTargetAvailability() {
        return targetAvailability;
    }

    public void setTargetAvailability(BigDecimal targetAvailability) {
        this.targetAvailability = targetAvailability;
    }
}
