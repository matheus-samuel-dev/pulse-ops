package com.pulseops.domain.monitoring;

import com.pulseops.domain.common.AuditableEntity;
import com.pulseops.domain.system.MonitoredSystem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "health_checks",
        indexes = {
                @Index(name = "idx_health_checks_system_checked", columnList = "monitored_system_id,checked_at"),
                @Index(name = "idx_health_checks_system_success_checked", columnList = "monitored_system_id,success,checked_at")
        }
)
public class HealthCheck extends AuditableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monitored_system_id", nullable = false)
    private MonitoredSystem monitoredSystem;

    @NotNull
    @Column(name = "checked_at", nullable = false)
    private OffsetDateTime checkedAt;

    @Column(name = "http_status")
    private Integer httpStatus;

    @PositiveOrZero
    @Column(name = "response_time_ms", nullable = false)
    private long responseTimeMs;

    @Column(nullable = false)
    private boolean success;

    @Size(max = 2000)
    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name="failure_type",nullable=false,length=30) private String failureType="LEGACY_UNCLASSIFIED";
    public String getFailureType(){return failureType;}
    public void setFailureType(String value){failureType=value;}

    public HealthCheck() {
    }

    public MonitoredSystem getMonitoredSystem() {
        return monitoredSystem;
    }

    public void setMonitoredSystem(MonitoredSystem monitoredSystem) {
        this.monitoredSystem = monitoredSystem;
    }

    public OffsetDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(OffsetDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public void setHttpStatus(Integer httpStatus) {
        this.httpStatus = httpStatus;
    }

    public long getResponseTimeMs() {
        return responseTimeMs;
    }

    public void setResponseTimeMs(long responseTimeMs) {
        this.responseTimeMs = responseTimeMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
