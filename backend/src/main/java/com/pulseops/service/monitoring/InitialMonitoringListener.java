package com.pulseops.service.monitoring;

import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.service.EventRecorder;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Starts only after a successful commit. HTTP monitoring never depends on an open browser. */
@Component
@ConditionalOnProperty(prefix="pulseops.monitoring", name="enabled", havingValue="true", matchIfMissing=true)
public class InitialMonitoringListener {
    private final MonitoredSystemRepository systems;
    private final MonitoringService monitoring;
    private final Executor executor;
    public InitialMonitoringListener(MonitoredSystemRepository systems, MonitoringService monitoring,
            @Qualifier("monitoringTaskExecutor") Executor executor) { this.systems=systems;this.monitoring=monitoring;this.executor=executor; }
    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    public void created(EventRecorder.Recorded event) {
        if (!event.type().equals("SYSTEM_CREATED") && !event.type().equals("SYSTEM_UPDATED")) return;
        try { executor.execute(() -> {
            try { systems.findById(event.systemId()).filter(system -> system.isActive() && !system.isMaintenance()).ifPresent(system -> monitoring.checkSystem(system.getId())); }
            catch(RuntimeException failure) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Primeira verificação adiada para o próximo ciclo: sistema {} ({})",event.systemId(),failure.getClass().getSimpleName()); }
        }); } catch(java.util.concurrent.RejectedExecutionException saturated) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("Fila de monitoramento ocupada; sistema {} será verificado no próximo ciclo",event.systemId()); }
    }
}
