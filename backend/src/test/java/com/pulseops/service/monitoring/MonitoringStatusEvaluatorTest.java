package com.pulseops.service.monitoring;

import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import com.pulseops.dto.monitoring.HealthProbeResult;
import com.pulseops.dto.monitoring.MonitoringDecision;
import com.pulseops.dto.monitoring.ProbeFailureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@DisplayName("MonitoringStatusEvaluator")
class MonitoringStatusEvaluatorTest {

    private MonitoringStatusEvaluator evaluator;
    private MonitoredSystem system;

    @BeforeEach
    void setUp() {
        evaluator = new MonitoringStatusEvaluator(3, 5, 2);
        system = monitoredSystem(200, 1_000L);
    }

    @Nested
    @DisplayName("successful probes")
    class SuccessfulProbes {

        @Test
        void shouldBeOperationalWhenStatusAndLatencyMeetExpectations() {
            MonitoringDecision decision = evaluator.evaluate(
                    system,
                    HealthProbeResult.response(200, 120),
                    List.of(successfulCheck())
            );

            assertThat(decision.status()).isEqualTo(SystemStatus.OPERATIONAL);
            assertThat(decision.successful()).isTrue();
            assertThat(decision.reason()).isEqualTo("Verificação bem-sucedida");
        }

        @Test
        void shouldTreatLatencyExactlyAtThresholdAsOperational() {
            MonitoringDecision decision = evaluator.evaluate(
                    system,
                    HealthProbeResult.response(200, 1_000),
                    List.of()
            );

            assertThat(decision.status()).isEqualTo(SystemStatus.OPERATIONAL);
            assertThat(decision.successful()).isTrue();
        }

        @Test
        void shouldBeDegradedButSuccessfulWhenLatencyExceedsThreshold() {
            MonitoringDecision decision = evaluator.evaluate(
                    system,
                    HealthProbeResult.response(200, 1_001),
                    List.of()
            );

            assertThat(decision.status()).isEqualTo(SystemStatus.DEGRADED);
            assertThat(decision.successful()).isTrue();
            assertThat(decision.reason()).contains("1001 ms", "1000 ms");
        }

        @Test
        void shouldBeDegradedWhenRecentFailuresIndicateInstability() {
            MonitoringDecision decision = evaluator.evaluate(
                    system,
                    HealthProbeResult.response(200, 90),
                    List.of(failedCheck(), successfulCheck(), failedCheck(), successfulCheck())
            );

            assertThat(decision.status()).isEqualTo(SystemStatus.DEGRADED);
            assertThat(decision.successful()).isTrue();
            assertThat(decision.reason()).contains("2 falhas recentes");
        }

        @Test
        void shouldOnlyEvaluateFailuresInsideConfiguredRecentWindow() {
            MonitoringDecision decision = evaluator.evaluate(
                    system,
                    HealthProbeResult.response(200, 90),
                    List.of(successfulCheck(), successfulCheck(), successfulCheck(), successfulCheck(),
                            failedCheck(), failedCheck())
            );

            assertThat(decision.status()).isEqualTo(SystemStatus.OPERATIONAL);
        }
    }

    @Nested
    @DisplayName("unsuccessful probes")
    class UnsuccessfulProbes {

        @Test
        void shouldInitiallyDegradeOnUnexpectedNonCriticalHttpStatus() {
            MonitoringDecision decision = evaluator.evaluate(
                    system,
                    HealthProbeResult.response(404, 70),
                    List.of(successfulCheck())
            );

            assertThat(decision.status()).isEqualTo(SystemStatus.DEGRADED);
            assertThat(decision.successful()).isFalse();
            assertThat(decision.reason()).contains("404", "200");
        }

        @Test
        void shouldDegradeFirstServerFailure() {
            MonitoringDecision decision = evaluator.evaluate(
                    system,
                    HealthProbeResult.response(503, 70),
                    List.of()
            );

            assertThat(decision.status()).isEqualTo(SystemStatus.DEGRADED);
            assertThat(decision.successful()).isFalse();
        }

        @Test
        void shouldBeDownWhenUnexpectedStatusesReachConsecutiveFailureThreshold() {
            MonitoringDecision decision = evaluator.evaluate(
                    system,
                    HealthProbeResult.response(401, 70),
                    List.of(failedCheck(), failedCheck(), successfulCheck())
            );

            assertThat(decision.status()).isEqualTo(SystemStatus.DOWN);
            assertThat(decision.successful()).isFalse();
        }

        @Test
        void shouldStopCountingConsecutiveFailuresAtFirstSuccess() {
            MonitoringDecision decision = evaluator.evaluate(
                    system,
                    HealthProbeResult.response(401, 70),
                    List.of(failedCheck(), successfulCheck(), failedCheck(), failedCheck())
            );

            assertThat(decision.status()).isEqualTo(SystemStatus.DEGRADED);
        }

        @ParameterizedTest(name = "{0} should make the system down")
        @EnumSource(value = ProbeFailureType.class, names = "NONE", mode = EnumSource.Mode.EXCLUDE)
        void shouldDegradeTransportFailureOrFlagSecurityConfiguration(ProbeFailureType failureType) {
            MonitoringDecision decision = evaluator.evaluate(
                    system,
                    HealthProbeResult.failure(failureType, 2_000, "external failure"),
                    List.of(successfulCheck())
            );

            assertThat(decision.status()).isEqualTo(failureType == ProbeFailureType.SECURITY_POLICY ? SystemStatus.CONFIGURATION_REQUIRED : SystemStatus.DEGRADED);
            assertThat(decision.successful()).isFalse();
            assertThat(decision.reason()).isNotBlank();
        }
    }

    @Nested
    @DisplayName("input validation")
    class InputValidation {

        @Test
        void shouldTreatNullPreviousChecksAsEmptyHistory() {
            MonitoringDecision decision = evaluator.evaluate(
                    system,
                    HealthProbeResult.response(200, 100),
                    null
            );

            assertThat(decision.status()).isEqualTo(SystemStatus.OPERATIONAL);
        }

        @Test
        void shouldRejectNullSystemAndProbe() {
            assertThatNullPointerException()
                    .isThrownBy(() -> evaluator.evaluate(null, HealthProbeResult.response(200, 1), List.of()))
                    .withMessage("system é obrigatório");
            assertThatNullPointerException()
                    .isThrownBy(() -> evaluator.evaluate(system, null, List.of()))
                    .withMessage("probe é obrigatório");
        }

        @Test
        void shouldRejectInvalidConfiguration() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new MonitoringStatusEvaluator(0, 5, 2))
                    .withMessageContaining("failuresForDown");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new MonitoringStatusEvaluator(3, 0, 1))
                    .withMessageContaining("recentWindowSize");
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new MonitoringStatusEvaluator(3, 3, 4))
                    .withMessageContaining("recentFailuresForDegraded");
        }
    }

    private MonitoredSystem monitoredSystem(int expectedStatusCode, long latencyThresholdMs) {
        MonitoredSystem monitoredSystem = new MonitoredSystem();
        monitoredSystem.setExpectedStatusCode(expectedStatusCode);
        monitoredSystem.setLatencyThresholdMs(latencyThresholdMs);
        return monitoredSystem;
    }

    private HealthCheck successfulCheck() {
        HealthCheck check = new HealthCheck();
        check.setSuccess(true);
        return check;
    }

    private HealthCheck failedCheck() {
        HealthCheck check = new HealthCheck();
        check.setSuccess(false);
        return check;
    }
}
