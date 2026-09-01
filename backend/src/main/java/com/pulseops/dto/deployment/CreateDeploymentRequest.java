package com.pulseops.dto.deployment;

import com.pulseops.domain.system.Environment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateDeploymentRequest(
        @NotNull UUID systemId,
        @NotBlank @Size(max = 80) String version,
        @NotNull Environment environment,
        @Pattern(regexp = "^[a-fA-F0-9]{7,64}$", message = "commitHash deve ser hexadecimal") String commitHash,
        @Size(max = 2000) String description,
        OffsetDateTime deployedAt
) {
    public CreateDeploymentCommand toCommand() {
        return new CreateDeploymentCommand(version, environment, commitHash, description, deployedAt);
    }
}
