package com.pulseops.dto.deployment;

import com.pulseops.domain.system.Environment;

import java.time.OffsetDateTime;

public record CreateDeploymentCommand(
        String version,
        Environment environment,
        String commitHash,
        String description,
        OffsetDateTime deployedAt
) {
}
