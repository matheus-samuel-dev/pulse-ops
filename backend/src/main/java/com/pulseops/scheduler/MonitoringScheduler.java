package com.pulseops.scheduler;

import com.pulseops.dto.monitoring.MonitoringBatchResult;
import com.pulseops.service.monitoring.MonitoringBatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "pulseops.monitoring", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitoringScheduler.class);

    private final MonitoringBatchService monitoringBatchService;

    public MonitoringScheduler(MonitoringBatchService monitoringBatchService) {
        this.monitoringBatchService = monitoringBatchService;
    }

    @Scheduled(
            fixedDelayString = "${pulseops.monitoring.fixed-delay-ms:60000}",
            initialDelayString = "${pulseops.monitoring.initial-delay-ms:15000}"
    )
    public void execute() {
        MonitoringBatchResult result = monitoringBatchService.checkAllActiveSystems();
        if (result.failures().isEmpty()) {
            log.debug("Monitoring cycle completed: {}/{} systems checked", result.completed(), result.attempted());
            return;
        }
        log.warn("Monitoring cycle completed with {} isolated failure(s): {}",
                result.failures().size(), result.failures());
    }
}
