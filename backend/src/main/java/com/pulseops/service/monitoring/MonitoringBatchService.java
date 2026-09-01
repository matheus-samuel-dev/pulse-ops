package com.pulseops.service.monitoring;

import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.monitoring.MonitoringBatchResult;
import com.pulseops.dto.monitoring.MonitoringBatchResult.MonitoringFailure;
import com.pulseops.repository.MonitoredSystemRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/** Runs checks independently so an unhealthy external API never prevents checks for other systems. */
@Service
public class MonitoringBatchService {

    private final MonitoredSystemRepository systemRepository;
    private final MonitoringService monitoringService;
    private final Executor monitoringTaskExecutor;

    public MonitoringBatchService(
            MonitoredSystemRepository systemRepository,
            MonitoringService monitoringService,
            @Qualifier("monitoringTaskExecutor") Executor monitoringTaskExecutor
    ) {
        this.systemRepository = Objects.requireNonNull(systemRepository);
        this.monitoringService = Objects.requireNonNull(monitoringService);
        this.monitoringTaskExecutor = Objects.requireNonNull(monitoringTaskExecutor);
    }

    public MonitoringBatchResult checkAllActiveSystems() {
        List<MonitoredSystem> systems = systemRepository.findAllByActiveTrueOrderByNameAsc();
        List<CompletableFuture<MonitoringFailure>> checks = systems.stream()
                .map(this::submitCheck)
                .toList();
        List<MonitoringFailure> failures = checks.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();

        return new MonitoringBatchResult(
                systems.size(), systems.size() - failures.size(), failures);
    }

    private CompletableFuture<MonitoringFailure> submitCheck(MonitoredSystem system) {
        try {
            return CompletableFuture
                    .supplyAsync(() -> check(system), monitoringTaskExecutor)
                    .exceptionally(exception -> failure(system, unwrap(exception)));
        } catch (RuntimeException exception) {
            // A saturated or shutting-down executor must not abort the whole monitoring cycle.
            return CompletableFuture.completedFuture(failure(system, exception));
        }
    }

    private MonitoringFailure check(MonitoredSystem system) {
        try {
            monitoringService.checkSystem(system.getId());
            return null;
        } catch (RuntimeException exception) {
            return failure(system, exception);
        }
    }

    private MonitoringFailure failure(MonitoredSystem system, Throwable throwable) {
        String reason = throwable.getMessage() == null
                ? throwable.getClass().getSimpleName()
                : throwable.getMessage();
        return new MonitoringFailure(system.getId(), system.getName(), reason);
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }
}
