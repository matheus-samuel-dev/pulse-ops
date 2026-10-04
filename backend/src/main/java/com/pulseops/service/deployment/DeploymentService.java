package com.pulseops.service.deployment;

import com.pulseops.domain.deployment.Deployment;
import com.pulseops.domain.deployment.DeploymentStatus;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.deployment.CreateDeploymentCommand;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.exception.InvalidStateTransitionException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.DeploymentRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class DeploymentService {

    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.pulseops.service.EventRecorder events;
    private static final Pattern SEMANTIC_VERSION = Pattern.compile(
            "^v?(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?"
                    + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");
    private static final Pattern COMMIT_HASH = Pattern.compile("^[a-fA-F0-9]{7,64}$");
    private static final int MAX_DESCRIPTION_LENGTH = 2_000;

    private final DeploymentRepository deploymentRepository;
    private final MonitoredSystemRepository systemRepository;
    private final Clock clock;

    public DeploymentService(
            DeploymentRepository deploymentRepository,
            MonitoredSystemRepository systemRepository,
            Clock clock
    ) {
        this.deploymentRepository = Objects.requireNonNull(deploymentRepository);
        this.systemRepository = Objects.requireNonNull(systemRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public Deployment create(UUID systemId, CreateDeploymentCommand command) {
        Objects.requireNonNull(command, "command é obrigatório");
        MonitoredSystem system = findSystem(systemId);
        String version = normalizeAndValidateVersion(command.version());
        String commitHash = normalizeAndValidateCommitHash(command.commitHash());
        if (command.environment() == null) {
            throw new BusinessRuleException("O ambiente do deploy é obrigatório");
        }
        String description = normalizeDescription(command.description());
        OffsetDateTime deployedAt = command.deployedAt() == null
                ? OffsetDateTime.now(clock)
                : command.deployedAt();
        if (deployedAt.isAfter(OffsetDateTime.now(clock))) {
            throw new BusinessRuleException("A data do deploy não pode estar no futuro");
        }

        Deployment deployment = new Deployment();
        deployment.setMonitoredSystem(system);
        deployment.setVersion(version);
        deployment.setEnvironment(command.environment());
        deployment.setStatus(DeploymentStatus.PENDING);
        deployment.setDeployedAt(deployedAt);
        deployment.setCommitHash(commitHash);
        deployment.setDescription(description);
        deployment.setSource("MANUAL");
        Deployment saved=deploymentRepository.save(deployment); record(saved,"DEPLOYMENT_RECORDED"); return saved;
    }

    @Transactional
    public Deployment start(UUID deploymentId) {
        return transition(deploymentId, DeploymentStatus.PENDING, DeploymentStatus.RUNNING, null);
    }

    @Transactional
    public Deployment succeed(UUID deploymentId, Long durationSeconds) {
        return transition(
                deploymentId, DeploymentStatus.RUNNING, DeploymentStatus.SUCCESS, durationSeconds);
    }

    @Transactional
    public Deployment fail(UUID deploymentId, Long durationSeconds) {
        return transition(
                deploymentId, DeploymentStatus.RUNNING, DeploymentStatus.FAILED, durationSeconds);
    }

    @Transactional
    public Deployment rollback(UUID deploymentId, Long durationSeconds) {
        Deployment deployment = findDeployment(deploymentId);
        if (deployment.getStatus() != DeploymentStatus.SUCCESS
                && deployment.getStatus() != DeploymentStatus.FAILED) {
            throw new InvalidStateTransitionException(
                    "deployment", deployment.getStatus(), DeploymentStatus.ROLLED_BACK);
        }
        if(durationSeconds!=null) validateDuration(durationSeconds);
        deployment.setStatus(DeploymentStatus.ROLLED_BACK);
        deployment.setDurationSeconds(durationSeconds);
        Deployment saved=deploymentRepository.save(deployment);record(saved,"DEPLOYMENT_UPDATED");return saved;
    }

    @Transactional(readOnly = true)
    public List<Deployment> findBySystem(UUID systemId) {
        findSystem(systemId);
        return deploymentRepository.findByMonitoredSystemIdOrderByDeployedAtDesc(systemId);
    }

    private Deployment transition(
            UUID deploymentId,
            DeploymentStatus requiredCurrentStatus,
            DeploymentStatus targetStatus,
            Long durationSeconds
    ) {
        Deployment deployment = findDeployment(deploymentId);
        if (deployment.getStatus() != requiredCurrentStatus) {
            throw new InvalidStateTransitionException(
                    "deployment", deployment.getStatus(), targetStatus);
        }
        if (durationSeconds != null) {
            validateDuration(durationSeconds);
            deployment.setDurationSeconds(durationSeconds);
        }
        deployment.setStatus(targetStatus);
        Deployment saved=deploymentRepository.save(deployment);record(saved,targetStatus==DeploymentStatus.FAILED ? "DEPLOYMENT_FAILED" : "DEPLOYMENT_UPDATED");return saved;
    }

    public Deployment succeed(UUID id,long seconds){return succeed(id,Long.valueOf(seconds));}
    public Deployment fail(UUID id,long seconds){return fail(id,Long.valueOf(seconds));}
    public Deployment rollback(UUID id,long seconds){return rollback(id,Long.valueOf(seconds));}
    private void record(Deployment deployment,String type){
        if(events!=null) events.recordResourceInEnvironment(deployment.getMonitoredSystem(),deployment.getEnvironment(),type, type.equals("DEPLOYMENT_FAILED") ? "ERROR" : "INFO", "Registro de implantação atualizado", "Versão "+deployment.getVersion()+" · origem: "+deployment.getSource(), "Registro manual via API",deployment.getId(),deployment.getStatus().name());
    }
    private void validateDuration(long durationSeconds) {
        if (durationSeconds < 0) {
            throw new BusinessRuleException("A duração do deploy não pode ser negativa");
        }
    }

    private String normalizeAndValidateVersion(String version) {
        if (version == null || version.isBlank()) {
            throw new BusinessRuleException("A versão do deploy é obrigatória");
        }
        String normalized = version.trim();
        if (!SEMANTIC_VERSION.matcher(normalized).matches()) {
            throw new BusinessRuleException(
                    "A versão do deploy deve seguir Semantic Versioning (por exemplo, 1.4.2)");
        }
        return normalized;
    }

    private String normalizeAndValidateCommitHash(String commitHash) {
        if (commitHash == null || commitHash.isBlank()) {
            return null;
        }
        String normalized = commitHash.trim();
        if (!COMMIT_HASH.matcher(normalized).matches()) {
            throw new BusinessRuleException("O hash do commit deve conter de 7 a 64 caracteres hexadecimais");
        }
        return normalized.toLowerCase();
    }

    private String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String normalized = description.trim();
        if (normalized.length() > MAX_DESCRIPTION_LENGTH) {
            throw new BusinessRuleException(
                    "A descrição do deploy excede " + MAX_DESCRIPTION_LENGTH + " caracteres");
        }
        return normalized;
    }

    private MonitoredSystem findSystem(UUID systemId) {
        return systemRepository.findById(systemId)
                .orElseThrow(() -> new ResourceNotFoundException("Monitored system", systemId));
    }

    private Deployment findDeployment(UUID deploymentId) {
        return deploymentRepository.findById(deploymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Deployment", deploymentId));
    }
}
