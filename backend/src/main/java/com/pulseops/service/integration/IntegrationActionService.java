package com.pulseops.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.pulseops.client.IntegrationHttpClient;
import com.pulseops.domain.integration.*;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.*;
import com.pulseops.security.AccountScope;
import com.pulseops.security.IntegrationCredentialCipher;
import com.pulseops.service.MonitoredSystemService;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

@Service
public class IntegrationActionService {
    private final IntegrationConnectionRepository connections;
    private final IntegrationRunRepository runs;
    private final MonitoredSystemRepository systemRepository;
    private final MonitoredSystemService systems;
    private final IntegrationHttpClient http;
    private final IntegrationCredentialCipher cipher;
    private final IntegrationRunPersistence persistence;
    public IntegrationActionService(IntegrationConnectionRepository connections,IntegrationRunRepository runs,
            MonitoredSystemRepository systemRepository,MonitoredSystemService systems,IntegrationHttpClient http,
            IntegrationCredentialCipher cipher,IntegrationRunPersistence persistence){this.connections=connections;this.runs=runs;this.systemRepository=systemRepository;this.systems=systems;this.http=http;this.cipher=cipher;this.persistence=persistence;}
    public Page<IntegrationRun> list(UUID systemId,int page,int size){if(systemId!=null) systems.findById(systemId);return runs.list(AccountScope.userId(),systemId,PageRequest.of(page,size));}
    public IntegrationRun request(String slug,UUID systemId,boolean authorizationConfirmed){
        if(!List.of("ai-web-auditor","nexus-flow").contains(slug)) throw new BusinessRuleException("Esta integração permite somente monitoramento HTTP");
        if(slug.equals("ai-web-auditor") && !authorizationConfirmed) throw new BusinessRuleException("Confirme que possui autorização para auditar o sistema");
        IntegrationConnection connection=connections.findByOwnerIdAndSlug(AccountScope.userId(),slug).orElseThrow(() -> new BusinessRuleException("Configure a integração antes de solicitar uma execução"));
        return dispatch(connection,systems.findEntity(systemId),null);
    }
    public IntegrationRun dispatch(IntegrationConnection connection,MonitoredSystem target,UUID eventId){
        IntegrationRun run=persistence.start(connection,target,eventId);
        return send(connection,target,run);
    }
    public void deliverQueued(IntegrationRun run) {
        if (run.getSystemId() == null) {
            run.setState("FAILED"); run.setMessage("O sistema foi excluído antes da entrega do evento."); runs.save(run); return;
        }
        IntegrationConnection connection=connections.findById(run.getConnectionId()).orElseThrow(() -> new BusinessRuleException("Integração removida"));
        MonitoredSystem target=systemRepository.findById(run.getSystemId()).orElseThrow(() -> new BusinessRuleException("Sistema removido"));
        send(connection,target,run);
    }
    private IntegrationRun send(IntegrationConnection connection,MonitoredSystem target,IntegrationRun run) {
        UUID eventId=run.getEventId();
        try {
            Map<String,Object> body=connection.getSlug().equals("ai-web-auditor") ? Map.of("url",target.getBaseUrl(),"projectName",target.getName(),"authorizationConfirmed",true,"allowDestructiveActions",false,"testEnvironment",false)
                : Map.of("source","pulseops","type",eventId==null ? "MANUAL_REQUEST" : "SYSTEM_DOWN","eventId",eventId==null ? run.getId().toString() : eventId.toString(),"runId",run.getId().toString(),"systemId",target.getId().toString(),"systemName",target.getName(),"url",target.getBaseUrl());
            IntegrationHttpClient.Result result=http.exchange(connection.getBaseUrl(),connection.getActionPath(),HttpMethod.POST,body,cipher.decrypt(connection.getEncryptedToken()),run.getId().toString());
            apply(run,connection,result,true);
        } catch(BusinessRuleException failure){run.setState("FAILED");run.setMessage(failure.getMessage());}
        return persistence.save(run,target,"PENDING");
    }
    public IntegrationRun refresh(UUID id){
        IntegrationRun run=runs.findByIdAndOwnerId(id,AccountScope.userId()).orElseThrow(() -> new ResourceNotFoundException("Execução",id));
        if(!run.getSlug().equals("ai-web-auditor")) throw new BusinessRuleException("O Nexus Flow informa conclusão pelo callback autenticado; uma resposta HTTP aceita não comprova execução do workflow");
        if(run.getExternalId()==null) throw new BusinessRuleException("A solicitação falhou antes de gerar uma auditoria. Solicite uma nova execução");
        IntegrationConnection connection=connections.findById(run.getConnectionId()).orElseThrow(() -> new BusinessRuleException("Reconfigure a integração"));
        MonitoredSystem target=systems.findEntity(run.getSystemId());
        String oldState=run.getState();
        IntegrationHttpClient.Result result=http.exchange(connection.getBaseUrl(),connection.getActionPath()+"/"+run.getExternalId(),HttpMethod.GET,null,cipher.decrypt(connection.getEncryptedToken()),null);
        apply(run,connection,result,false);return persistence.save(run,target,oldState);
    }
    private void apply(IntegrationRun run,IntegrationConnection connection,IntegrationHttpClient.Result result,boolean creation){
        run.setHttpStatus(result.httpStatus());
        if(result.httpStatus()>=300){run.setMessage(result.httpStatus()==401 || result.httpStatus()==403 ? "A integração recusou a credencial. Atualize o token de acesso." : "O serviço retornou HTTP "+result.httpStatus()+". Verifique a configuração.");if(creation)run.setState("FAILED");return;}
        JsonNode body=result.body();
        if(run.getSlug().equals("ai-web-auditor")) {
            if(body==null || !body.hasNonNull("id") || !body.hasNonNull("status")) throw new BusinessRuleException("O AI Web Auditor retornou um contrato inválido: id e status são obrigatórios");
            try{run.setExternalId(UUID.fromString(body.path("id").asText()).toString());}catch(IllegalArgumentException e){throw new BusinessRuleException("O identificador recebido do AI Web Auditor é inválido");}
            String state=body.path("status").asText();
            if(!List.of("PENDING","RUNNING","COMPLETED","FAILED","CANCELLED").contains(state))throw new BusinessRuleException("Estado de auditoria não reconhecido");
            run.setState(state);run.setMessage("Estado consultado no AI Web Auditor.");
            if(state.equals("COMPLETED") && body.hasNonNull("overallScore") && body.path("overallScore").isIntegralNumber()) {
                int score=body.path("overallScore").asInt();if(score>=0 && score<=100)run.setOverallScore(score);
            }
            if(state.equals("COMPLETED") && connection.getPublicUrl()!=null) run.setReportUrl(connection.getPublicUrl()+"/audits/"+run.getExternalId());
        } else {run.setState("ACCEPTED");run.setMessage("O Nexus Flow recebeu a solicitação. A conclusão depende do callback do workflow.");}
    }
    public IntegrationRun callback(UUID id,String state,String externalId){
        IntegrationRun run=runs.findByIdAndOwnerId(id,AccountScope.userId()).orElseThrow(() -> new ResourceNotFoundException("Execução",id));
        if(!run.getSlug().equals("nexus-flow") || !List.of("RUNNING","COMPLETED","FAILED","CANCELLED").contains(state))throw new BusinessRuleException("Atualização de execução inválida");
        if(List.of("COMPLETED","FAILED","CANCELLED").contains(run.getState()))throw new BusinessRuleException("Esta execução já foi finalizada");
        String old=run.getState();run.setState(state);run.setExternalId(externalId);run.setMessage("Estado recebido do workflow autenticado.");
        return persistence.save(run,systems.findEntity(run.getSystemId()),old);
    }
}
