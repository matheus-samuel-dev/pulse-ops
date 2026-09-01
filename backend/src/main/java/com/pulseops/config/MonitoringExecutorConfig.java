package com.pulseops.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class MonitoringExecutorConfig {

    @Bean(name = "monitoringTaskExecutor")
    public ThreadPoolTaskExecutor monitoringTaskExecutor(
            @Value("${pulseops.monitoring.concurrency:4}") int concurrency,
            @Value("${pulseops.monitoring.queue-capacity:256}") int queueCapacity
    ) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("Monitoring concurrency must be positive");
        }
        if (queueCapacity < 0) {
            throw new IllegalArgumentException("Monitoring queue capacity cannot be negative");
        }

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(concurrency);
        executor.setMaxPoolSize(concurrency);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("pulseops-monitor-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
