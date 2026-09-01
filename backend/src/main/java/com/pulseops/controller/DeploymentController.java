package com.pulseops.controller;

import com.pulseops.dto.deployment.CreateDeploymentRequest;
import com.pulseops.dto.deployment.DeploymentResponse;
import com.pulseops.dto.deployment.DeploymentTransitionRequest;
import com.pulseops.service.OperationsQueryService;
import com.pulseops.service.deployment.DeploymentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/deployments")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Deployments", description = "Registro e transições de deploy")
public class DeploymentController {

    private final DeploymentService deploymentService;
    private final OperationsQueryService queryService;

    public DeploymentController(DeploymentService deploymentService, OperationsQueryService queryService) {
        this.deploymentService = deploymentService;
        this.queryService = queryService;
    }

    @GetMapping
    public List<DeploymentResponse> findAll(@RequestParam(required = false) UUID systemId) {
        return queryService.deployments(systemId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public DeploymentResponse create(@Valid @RequestBody CreateDeploymentRequest request) {
        return DeploymentResponse.from(deploymentService.create(request.systemId(), request.toCommand()));
    }

    @PatchMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public DeploymentResponse start(@PathVariable UUID id) {
        return DeploymentResponse.from(deploymentService.start(id));
    }

    @PatchMapping("/{id}/success")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public DeploymentResponse succeed(
            @PathVariable UUID id,
            @Valid @RequestBody DeploymentTransitionRequest request
    ) {
        return DeploymentResponse.from(deploymentService.succeed(id, request.durationSeconds()));
    }

    @PatchMapping("/{id}/failure")
    @PreAuthorize("hasAnyRole('ADMIN', 'DEVELOPER')")
    public DeploymentResponse fail(
            @PathVariable UUID id,
            @Valid @RequestBody DeploymentTransitionRequest request
    ) {
        return DeploymentResponse.from(deploymentService.fail(id, request.durationSeconds()));
    }

    @PatchMapping("/{id}/rollback")
    @PreAuthorize("hasRole('ADMIN')")
    public DeploymentResponse rollback(
            @PathVariable UUID id,
            @Valid @RequestBody DeploymentTransitionRequest request
    ) {
        return DeploymentResponse.from(deploymentService.rollback(id, request.durationSeconds()));
    }
}
