package com.pulseops.service.integration;
import com.pulseops.domain.integration.*;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.repository.IntegrationRunRepository;
import com.pulseops.service.EventRecorder;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IntegrationRunPersistence {
    private final IntegrationRunRepository runs;
    private final EventRecorder events;
    public IntegrationRunPersistence(IntegrationRunRepository runs,EventRecorder events){this.runs=runs;this.events=events;}
    @Transactional
    public IntegrationRun start(IntegrationConnection connection,MonitoredSystem system,UUID eventId) {
        IntegrationRun run=new IntegrationRun();run.setOwnerId(connection.getOwnerId());run.setConnectionId(connection.getId());
        run.setSystemId(system.getId());run.setSystemName(system.getName());run.setSlug(connection.getSlug());run.setState("PENDING");run.setEventId(eventId);
        run=runs.save(run);events.recordResource(system,"INTEGRATION_REQUESTED","INFO",connection.getSlug().equals("ai-web-auditor") ? "Auditoria solicitada" : "Workflow solicitado",IntegrationCatalog.find(connection.getSlug()).title(),IntegrationCatalog.find(connection.getSlug()).title(),run.getId(),run.getState());return run;
    }
    @Transactional
    public IntegrationRun save(IntegrationRun run,MonitoredSystem system,String oldState) {
        IntegrationRun saved=runs.save(run);
        if(!saved.getState().equals(oldState)) events.recordResource(system,saved.getState().equals("FAILED") ? "INTEGRATION_FAILED" : "INTEGRATION_RESULT",saved.getState().equals("FAILED") ? "WARNING" : "INFO","Execução da integração atualizada",saved.getSystemName()+": "+stateLabel(saved.getState()),IntegrationCatalog.find(saved.getSlug()).title(),saved.getId(),saved.getState());
        return saved;
    }
    private String stateLabel(String state){return switch(state){case "COMPLETED"->"concluída";case "FAILED"->"falhou";case "CANCELLED"->"cancelada";case "RUNNING"->"em execução";case "ACCEPTED"->"recebida pelo serviço";default->"aguardando processamento";};}
}
