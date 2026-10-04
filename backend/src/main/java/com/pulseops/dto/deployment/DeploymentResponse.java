package com.pulseops.dto.deployment;

import com.pulseops.domain.deployment.Deployment;
import com.pulseops.domain.deployment.DeploymentStatus;
import com.pulseops.domain.system.Environment;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DeploymentResponse(
        UUID id,
        UUID systemId,
        String systemName,
        String version,
        Environment environment,
        DeploymentStatus status,
        OffsetDateTime deployedAt,
        Long durationSeconds,
        String commitHash,
        String description,
        String source,
        String executionUrl
) {
    public static DeploymentResponse from(Deployment deployment) {
        return new DeploymentResponse(
                deployment.getId(), deployment.getMonitoredSystem().getId(), deployment.getMonitoredSystem().getName(),
                deployment.getVersion(), deployment.getEnvironment(), deployment.getStatus(), deployment.getDeployedAt(),
                deployment.getDurationSeconds(), deployment.getCommitHash(), deployment.getDescription(),deployment.getSource(),deployment.getExecutionUrl());
    }
}
