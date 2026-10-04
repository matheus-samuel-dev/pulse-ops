package com.pulseops.dto.deployment;

import jakarta.validation.constraints.PositiveOrZero;

public record DeploymentTransitionRequest(@PositiveOrZero Long durationSeconds) {
}
