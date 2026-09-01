package com.pulseops.service.monitoring;

import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.monitoring.MonitoringBatchResult;
import com.pulseops.repository.MonitoredSystemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MonitoringBatchService")
class MonitoringBatchServiceTest {

    @Mock
    private MonitoredSystemRepository systemRepository;
    @Mock
    private MonitoringService monitoringService;

    private MonitoringBatchService batchService;
    private ExecutorService concurrentExecutor;

    @BeforeEach
    void setUp() {
        batchService = new MonitoringBatchService(systemRepository, monitoringService, Runnable::run);
    }

    @AfterEach
    void tearDown() {
        if (concurrentExecutor != null) {
            concurrentExecutor.shutdownNow();
        }
    }

    @Test
    void shouldCheckEveryActiveSystemAndReportSuccesses() {
        MonitoredSystem first = system("PlaySpace");
        MonitoredSystem second = system("LogiTrack");
        when(systemRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(first, second));

        MonitoringBatchResult result = batchService.checkAllActiveSystems();

        assertThat(result.attempted()).isEqualTo(2);
        assertThat(result.completed()).isEqualTo(2);
        assertThat(result.failures()).isEmpty();
        verify(monitoringService, times(1)).checkSystem(first.getId());
        verify(monitoringService, times(1)).checkSystem(second.getId());
    }

    @Test
    void shouldIsolateOneFailureAndContinueCheckingRemainingSystems() {
        MonitoredSystem first = system("PlaySpace");
        MonitoredSystem second = system("LogiTrack");
        MonitoredSystem third = system("AI Web Auditor");
        when(systemRepository.findAllByActiveTrueOrderByNameAsc())
                .thenReturn(List.of(first, second, third));
        when(monitoringService.checkSystem(any(UUID.class))).thenAnswer(invocation -> {
            UUID systemId = invocation.getArgument(0);
            if (systemId.equals(second.getId())) {
                throw new IllegalStateException("remote client failed");
            }
            return null;
        });

        MonitoringBatchResult result = batchService.checkAllActiveSystems();

        assertThat(result.attempted()).isEqualTo(3);
        assertThat(result.failures()).hasSize(1);
        assertThat(result.completed()).isEqualTo(2);
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.systemId()).isEqualTo(second.getId());
            assertThat(failure.systemName()).isEqualTo("LogiTrack");
            assertThat(failure.reason()).isEqualTo("remote client failed");
        });
        verify(monitoringService).checkSystem(third.getId());
    }

    @Test
    void shouldStartAnotherSystemWhileOneExternalCheckIsStillSlow() throws Exception {
        MonitoredSystem slow = system("Slow API");
        MonitoredSystem fast = system("Fast API");
        when(systemRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(slow, fast));
        CountDownLatch slowStarted = new CountDownLatch(1);
        CountDownLatch releaseSlow = new CountDownLatch(1);
        CountDownLatch fastCompleted = new CountDownLatch(1);
        when(monitoringService.checkSystem(any(UUID.class))).thenAnswer(invocation -> {
            UUID systemId = invocation.getArgument(0);
            if (systemId.equals(slow.getId())) {
                slowStarted.countDown();
                if (!releaseSlow.await(2, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test did not release slow check");
                }
            } else {
                fastCompleted.countDown();
            }
            return null;
        });
        concurrentExecutor = Executors.newFixedThreadPool(2);
        batchService = new MonitoringBatchService(systemRepository, monitoringService, concurrentExecutor);

        CompletableFuture<MonitoringBatchResult> batch = CompletableFuture.supplyAsync(
                batchService::checkAllActiveSystems);
        try {
            assertThat(slowStarted.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(fastCompleted.await(1, TimeUnit.SECONDS))
                    .as("the fast API must not wait for the slow API")
                    .isTrue();
        } finally {
            releaseSlow.countDown();
        }

        assertThat(batch.get(2, TimeUnit.SECONDS)).satisfies(result -> {
            assertThat(result.attempted()).isEqualTo(2);
            assertThat(result.completed()).isEqualTo(2);
            assertThat(result.failures()).isEmpty();
        });
    }

    @Test
    void shouldReportExecutorRejectionAsAnIsolatedFailure() {
        MonitoredSystem system = system("Queued API");
        when(systemRepository.findAllByActiveTrueOrderByNameAsc()).thenReturn(List.of(system));
        batchService = new MonitoringBatchService(systemRepository, monitoringService, command -> {
            throw new RejectedExecutionException("executor saturated");
        });

        MonitoringBatchResult result = batchService.checkAllActiveSystems();

        assertThat(result.completed()).isZero();
        assertThat(result.failures()).singleElement().satisfies(failure -> {
            assertThat(failure.systemId()).isEqualTo(system.getId());
            assertThat(failure.reason()).isEqualTo("executor saturated");
        });
        verify(monitoringService, never()).checkSystem(any());
    }

    private MonitoredSystem system(String name) {
        MonitoredSystem system = new MonitoredSystem();
        system.setId(UUID.randomUUID());
        system.setName(name);
        system.setActive(true);
        return system;
    }
}
