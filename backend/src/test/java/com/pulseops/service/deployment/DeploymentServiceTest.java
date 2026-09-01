package com.pulseops.service.deployment;

import com.pulseops.domain.deployment.Deployment;
import com.pulseops.domain.deployment.DeploymentStatus;
import com.pulseops.domain.system.Environment;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.deployment.CreateDeploymentCommand;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.exception.InvalidStateTransitionException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.DeploymentRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeploymentService")
class DeploymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T15:00:00Z");
    private static final OffsetDateTime NOW_OFFSET = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Mock
    private DeploymentRepository deploymentRepository;
    @Mock
    private MonitoredSystemRepository systemRepository;

    private DeploymentService deploymentService;

    @BeforeEach
    void setUp() {
        deploymentService = new DeploymentService(
                deploymentRepository,
                systemRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        void shouldCreatePendingDeploymentWithNormalizedMetadata() {
            UUID systemId = UUID.randomUUID();
            MonitoredSystem system = system(systemId);
            when(systemRepository.findById(systemId)).thenReturn(Optional.of(system));
            when(deploymentRepository.save(any(Deployment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            CreateDeploymentCommand command = new CreateDeploymentCommand(
                    "  v2.4.1-rc.1+build.17  ",
                    Environment.PRODUCTION,
                    "  ABCDEF1234567  ",
                    "  Adds resilient health checks  ",
                    null
            );

            Deployment result = deploymentService.create(systemId, command);

            ArgumentCaptor<Deployment> captor = ArgumentCaptor.forClass(Deployment.class);
            verify(deploymentRepository).save(captor.capture());
            assertThat(result).isSameAs(captor.getValue());
            assertThat(result.getMonitoredSystem()).isSameAs(system);
            assertThat(result.getVersion()).isEqualTo("v2.4.1-rc.1+build.17");
            assertThat(result.getEnvironment()).isEqualTo(Environment.PRODUCTION);
            assertThat(result.getStatus()).isEqualTo(DeploymentStatus.PENDING);
            assertThat(result.getDeployedAt()).isEqualTo(NOW_OFFSET);
            assertThat(result.getCommitHash()).isEqualTo("abcdef1234567");
            assertThat(result.getDescription()).isEqualTo("Adds resilient health checks");
            assertThat(result.getDurationSeconds()).isNull();
        }

        @ParameterizedTest(name = "invalid version: {0}")
        @ValueSource(strings = {"1", "1.2", "01.2.3", "release-1.2.3", "1.2.3!"})
        void shouldRejectInvalidSemanticVersion(String version) {
            UUID systemId = UUID.randomUUID();
            when(systemRepository.findById(systemId)).thenReturn(Optional.of(system(systemId)));

            assertThatThrownBy(() -> deploymentService.create(
                    systemId,
                    new CreateDeploymentCommand(
                            version, Environment.STAGING, "abcdef1", null, NOW_OFFSET)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Semantic Versioning");

            verify(deploymentRepository, never()).save(any());
        }

        @Test
        void shouldRequireDeploymentVersion() {
            UUID systemId = UUID.randomUUID();
            when(systemRepository.findById(systemId)).thenReturn(Optional.of(system(systemId)));

            assertThatThrownBy(() -> deploymentService.create(
                    systemId,
                    new CreateDeploymentCommand(
                            "  ", Environment.STAGING, "abcdef1", null, NOW_OFFSET)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("version is required");

            verify(deploymentRepository, never()).save(any());
        }

        @Test
        void shouldRejectInvalidCommitEnvironmentFutureTimeAndLongDescription() {
            UUID systemId = UUID.randomUUID();
            when(systemRepository.findById(systemId)).thenReturn(Optional.of(system(systemId)));

            assertThatThrownBy(() -> deploymentService.create(
                    systemId,
                    new CreateDeploymentCommand("1.2.3", Environment.STAGING, "xyz1234", null, NOW_OFFSET)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Commit hash");
            assertThatThrownBy(() -> deploymentService.create(
                    systemId,
                    new CreateDeploymentCommand("1.2.3", null, null, null, NOW_OFFSET)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("environment");
            assertThatThrownBy(() -> deploymentService.create(
                    systemId,
                    new CreateDeploymentCommand(
                            "1.2.3", Environment.STAGING, null, null, NOW_OFFSET.plusNanos(1))))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("future");
            assertThatThrownBy(() -> deploymentService.create(
                    systemId,
                    new CreateDeploymentCommand(
                            "1.2.3", Environment.STAGING, null, "x".repeat(2_001), NOW_OFFSET)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("2000");

            verify(deploymentRepository, never()).save(any());
        }

        @Test
        void shouldFailWhenSystemDoesNotExist() {
            UUID systemId = UUID.randomUUID();
            when(systemRepository.findById(systemId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deploymentService.create(
                    systemId,
                    new CreateDeploymentCommand("1.2.3", Environment.PRODUCTION, null, null, null)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(systemId.toString());

            verify(deploymentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("lifecycle")
    class Lifecycle {

        @Test
        void shouldStartPendingDeployment() {
            Deployment deployment = deployment(DeploymentStatus.PENDING);
            when(deploymentRepository.findById(deployment.getId())).thenReturn(Optional.of(deployment));
            when(deploymentRepository.save(deployment)).thenReturn(deployment);

            Deployment result = deploymentService.start(deployment.getId());

            assertThat(result.getStatus()).isEqualTo(DeploymentStatus.RUNNING);
            assertThat(result.getDurationSeconds()).isNull();
            verify(deploymentRepository).save(deployment);
        }

        @Test
        void shouldSucceedOrFailRunningDeploymentAndRecordDuration() {
            Deployment successful = deployment(DeploymentStatus.RUNNING);
            Deployment failed = deployment(DeploymentStatus.RUNNING);
            when(deploymentRepository.findById(successful.getId())).thenReturn(Optional.of(successful));
            when(deploymentRepository.findById(failed.getId())).thenReturn(Optional.of(failed));
            when(deploymentRepository.save(any(Deployment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Deployment successResult = deploymentService.succeed(successful.getId(), 0);
            Deployment failedResult = deploymentService.fail(failed.getId(), 87);

            assertThat(successResult.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
            assertThat(successResult.getDurationSeconds()).isZero();
            assertThat(failedResult.getStatus()).isEqualTo(DeploymentStatus.FAILED);
            assertThat(failedResult.getDurationSeconds()).isEqualTo(87);
            verify(deploymentRepository).save(successful);
            verify(deploymentRepository).save(failed);
        }

        @Test
        void shouldRollbackSuccessfulAndFailedDeployments() {
            Deployment successful = deployment(DeploymentStatus.SUCCESS);
            Deployment failed = deployment(DeploymentStatus.FAILED);
            when(deploymentRepository.findById(successful.getId())).thenReturn(Optional.of(successful));
            when(deploymentRepository.findById(failed.getId())).thenReturn(Optional.of(failed));
            when(deploymentRepository.save(any(Deployment.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            deploymentService.rollback(successful.getId(), 120);
            deploymentService.rollback(failed.getId(), 240);

            assertThat(successful.getStatus()).isEqualTo(DeploymentStatus.ROLLED_BACK);
            assertThat(failed.getStatus()).isEqualTo(DeploymentStatus.ROLLED_BACK);
            assertThat(successful.getDurationSeconds()).isEqualTo(120);
            assertThat(failed.getDurationSeconds()).isEqualTo(240);
        }

        @Test
        void shouldRejectInvalidStateTransitionsWithoutSaving() {
            Deployment running = deployment(DeploymentStatus.RUNNING);
            Deployment pending = deployment(DeploymentStatus.PENDING);
            when(deploymentRepository.findById(running.getId())).thenReturn(Optional.of(running));
            when(deploymentRepository.findById(pending.getId())).thenReturn(Optional.of(pending));

            assertThatThrownBy(() -> deploymentService.start(running.getId()))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("RUNNING");
            assertThatThrownBy(() -> deploymentService.succeed(pending.getId(), 10))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("PENDING", "SUCCESS");
            assertThatThrownBy(() -> deploymentService.rollback(pending.getId(), 10))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("ROLLED_BACK");

            verify(deploymentRepository, never()).save(any());
        }

        @Test
        void shouldRejectNegativeDurationWithoutChangingStateOrSaving() {
            Deployment running = deployment(DeploymentStatus.RUNNING);
            when(deploymentRepository.findById(running.getId())).thenReturn(Optional.of(running));

            assertThatThrownBy(() -> deploymentService.fail(running.getId(), -1))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("negative");

            assertThat(running.getStatus()).isEqualTo(DeploymentStatus.RUNNING);
            assertThat(running.getDurationSeconds()).isNull();
            verify(deploymentRepository, never()).save(any());
        }

        @Test
        void shouldFailWhenDeploymentDoesNotExist() {
            UUID deploymentId = UUID.randomUUID();
            when(deploymentRepository.findById(deploymentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> deploymentService.start(deploymentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(deploymentId.toString());

            verify(deploymentRepository, never()).save(any());
        }
    }

    @Test
    void shouldValidateSystemBeforeListingDeployments() {
        UUID systemId = UUID.randomUUID();
        List<Deployment> deployments = List.of(deployment(DeploymentStatus.SUCCESS));
        when(systemRepository.findById(systemId)).thenReturn(Optional.of(system(systemId)));
        when(deploymentRepository.findByMonitoredSystemIdOrderByDeployedAtDesc(systemId))
                .thenReturn(deployments);

        assertThat(deploymentService.findBySystem(systemId)).containsExactlyElementsOf(deployments);

        verify(systemRepository).findById(systemId);
        verify(deploymentRepository).findByMonitoredSystemIdOrderByDeployedAtDesc(systemId);
    }

    private MonitoredSystem system(UUID id) {
        MonitoredSystem system = new MonitoredSystem();
        system.setId(id);
        system.setName("PlaySpace");
        return system;
    }

    private Deployment deployment(DeploymentStatus status) {
        Deployment deployment = new Deployment();
        deployment.setId(UUID.randomUUID());
        deployment.setStatus(status);
        deployment.setDeployedAt(NOW_OFFSET);
        deployment.setVersion("1.0.0");
        deployment.setEnvironment(Environment.PRODUCTION);
        return deployment;
    }
}
