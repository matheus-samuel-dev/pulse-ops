package com.pulseops.domain.deployment;

import com.pulseops.domain.common.AuditableEntity;
import com.pulseops.domain.system.Environment;
import com.pulseops.domain.system.MonitoredSystem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "deployments",
        indexes = {
                @Index(name = "idx_deployments_system_deployed", columnList = "monitored_system_id,deployed_at"),
                @Index(name = "idx_deployments_status_environment", columnList = "status,environment")
        }
)
public class Deployment extends AuditableEntity {

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monitored_system_id", nullable = false)
    private MonitoredSystem monitoredSystem;

    @NotBlank
    @Size(max = 80)
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._+-]{0,79}$")
    @Column(nullable = false, length = 80)
    private String version;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Environment environment;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeploymentStatus status = DeploymentStatus.PENDING;

    @NotNull
    @Column(name = "deployed_at", nullable = false)
    private OffsetDateTime deployedAt;

    @PositiveOrZero
    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Size(max = 64)
    @Pattern(regexp = "^[a-fA-F0-9]{7,64}$")
    @Column(name = "commit_hash", length = 64)
    private String commitHash;

    @Size(max = 2000)
    @Column(length = 2000)
    private String description;

    public Deployment() {
    }

    public MonitoredSystem getMonitoredSystem() {
        return monitoredSystem;
    }

    public void setMonitoredSystem(MonitoredSystem monitoredSystem) {
        this.monitoredSystem = monitoredSystem;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public DeploymentStatus getStatus() {
        return status;
    }

    public void setStatus(DeploymentStatus status) {
        this.status = status;
    }

    public OffsetDateTime getDeployedAt() {
        return deployedAt;
    }

    public void setDeployedAt(OffsetDateTime deployedAt) {
        this.deployedAt = deployedAt;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public void setCommitHash(String commitHash) {
        this.commitHash = commitHash;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
