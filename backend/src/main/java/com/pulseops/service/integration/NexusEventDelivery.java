package com.pulseops.service.integration;

import com.pulseops.domain.integration.IntegrationRun;
import com.pulseops.repository.*;
import com.pulseops.service.EventRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Durable queue: requests commit with the monitoring event, then leave the DB transaction before I/O. */
@Service
public class NexusEventDelivery {
    private static final Logger log=LoggerFactory.getLogger(NexusEventDelivery.class);
    private final IntegrationConnectionRepository connections;
    private final IntegrationRunRepository runs;
    private final MonitoredSystemRepository systems;
    private final IntegrationRunPersistence persistence;
    private final IntegrationActionService actions;
    public NexusEventDelivery(IntegrationConnectionRepository connections,IntegrationRunRepository runs,
            MonitoredSystemRepository systems,IntegrationRunPersistence persistence,IntegrationActionService actions){this.connections=connections;this.runs=runs;this.systems=systems;this.persistence=persistence;this.actions=actions;}
    @TransactionalEventListener(phase=TransactionPhase.BEFORE_COMMIT)
    public void enqueue(EventRecorder.Recorded event){
        if(!event.type().equals("SYSTEM_DOWN") || event.ownerId()==null) return;
        systems.findById(event.systemId()).ifPresent(system -> connections.findByOwnerIdAndSlugAndAutoDispatchTrue(event.ownerId(),"nexus-flow")
            .forEach(connection -> persistence.start(connection,system,event.id())));
    }
    @Scheduled(fixedDelayString="${pulseops.integrations.delivery-delay-ms:5000}",initialDelay=15000)
    public void deliver(){
        for(IntegrationRun run:runs.findTop10ByStateAndEventIdIsNotNullOrderByCreatedAtAsc("PENDING")){
            try{actions.deliverQueued(run);}catch(RuntimeException failure){log.warn("Não foi possível entregar a execução {} ({})",run.getId(),failure.getClass().getSimpleName());}
        }
    }
}
