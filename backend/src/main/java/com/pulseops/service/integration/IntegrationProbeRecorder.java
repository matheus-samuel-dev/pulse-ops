package com.pulseops.service.integration;
import com.pulseops.domain.integration.*;
import com.pulseops.dto.monitoring.HealthProbeResult;
import com.pulseops.repository.IntegrationProbeRepository;
import com.pulseops.service.EventRecorder;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class IntegrationProbeRecorder {
    private final IntegrationProbeRepository probes;private final EventRecorder events;private final Clock clock;
    public IntegrationProbeRecorder(IntegrationProbeRepository probes,EventRecorder events,Clock clock){this.probes=probes;this.events=events;this.clock=clock;}
    @Transactional public IntegrationProbe record(IntegrationConnection connection,HealthProbeResult result){
        IntegrationProbe probe=new IntegrationProbe();probe.setConnectionId(connection.getId());probe.setOwnerId(connection.getOwnerId());probe.setSlug(connection.getSlug());
        probe.setCheckedAt(OffsetDateTime.now(clock));probe.setHttpStatus(result.httpStatus());probe.setResponseTimeMs(result.httpStatus()==null?null:result.responseTimeMs());
        probe.setSuccess(result.httpStatus()!=null&&result.httpStatus()>=200&&result.httpStatus()<300);
        probe.setFailureType(result.httpStatus()==null?result.failureType().name():probe.isSuccess()?"NONE":"HTTP_STATUS");
        probe.setMessage(probe.isSuccess()?"Conexão realizada com sucesso":result.httpStatus()!=null?"O serviço retornou HTTP "+result.httpStatus():"Não foi possível conectar à integração; verifique o endereço e o tempo limite.");
        probe=probes.save(probe);events.recordAccount(connection.getOwnerId(),"INTEGRATION_CHECK",probe.isSuccess()?"SUCCESS":"ERROR","Conexão verificada",probe.getMessage(),IntegrationCatalog.find(connection.getSlug()).title(),probe.getId(),probe.isSuccess()?"SUCCESS":"FAILED");
        return probe;
    }
}
