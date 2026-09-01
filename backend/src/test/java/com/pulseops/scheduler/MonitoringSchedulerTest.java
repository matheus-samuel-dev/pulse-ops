package com.pulseops.scheduler;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pulseops.dto.monitoring.MonitoringBatchResult;
import com.pulseops.dto.monitoring.MonitoringBatchResult.MonitoringFailure;
import com.pulseops.service.monitoring.MonitoringBatchService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("MonitoringScheduler")
class MonitoringSchedulerTest {

    @Mock
    private MonitoringBatchService monitoringBatchService;

    @InjectMocks
    private MonitoringScheduler scheduler;

    @Test
    void shouldCompleteScheduledCycleWhenEveryActiveSystemWasChecked() {
        MonitoringBatchResult result = new MonitoringBatchResult(4, 4, List.of());
        when(monitoringBatchService.checkAllActiveSystems()).thenReturn(result);

        assertThatCode(scheduler::execute).doesNotThrowAnyException();

        verify(monitoringBatchService).checkAllActiveSystems();
    }

    @Test
    void shouldCompleteScheduledCycleWhenBatchIsolatesIndividualFailures() {
        MonitoringFailure failure = new MonitoringFailure(
                UUID.fromString("f06b5aca-5611-4754-9fd0-1f90501c3f9b"),
                "LogiTrack",
                "connection refused"
        );
        MonitoringBatchResult result = new MonitoringBatchResult(4, 3, List.of(failure));
        when(monitoringBatchService.checkAllActiveSystems()).thenReturn(result);

        assertThatCode(scheduler::execute).doesNotThrowAnyException();

        verify(monitoringBatchService).checkAllActiveSystems();
    }

    @Test
    void shouldExposeUnexpectedBatchFailureToSchedulingInfrastructure() {
        IllegalStateException failure = new IllegalStateException("repository unavailable");
        when(monitoringBatchService.checkAllActiveSystems()).thenThrow(failure);

        assertThatThrownBy(scheduler::execute).isSameAs(failure);

        verify(monitoringBatchService).checkAllActiveSystems();
    }
}
