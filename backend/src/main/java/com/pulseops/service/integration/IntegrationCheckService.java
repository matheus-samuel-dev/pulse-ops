package com.pulseops.service.integration;
import com.pulseops.client.HealthCheckClient;
import com.pulseops.config.IntegrationProperties;
import com.pulseops.domain.integration.*;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.integration.IntegrationCheckResponse;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.repository.*;
import com.pulseops.security.*;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;
@Service
public class IntegrationCheckService {
    private final IntegrationConnectionRepository connections;private final IntegrationProbeRepository probes;private final HealthCheckClient client;
    private final IntegrationCredentialCipher cipher;private final IntegrationProbeRecorder recorder;private final IntegrationProperties properties;private final Clock clock;
    private final ConcurrentHashMap<java.util.UUID,Object> locks=new ConcurrentHashMap<>();
    public IntegrationCheckService(IntegrationConnectionRepository connections,IntegrationProbeRepository probes,HealthCheckClient client,IntegrationCredentialCipher cipher,IntegrationProbeRecorder recorder,IntegrationProperties properties,Clock clock){this.connections=connections;this.probes=probes;this.client=client;this.cipher=cipher;this.recorder=recorder;this.properties=properties;this.clock=clock;}
    public IntegrationCheckResponse check(String slug){
        IntegrationCatalog.find(slug);
        IntegrationConnection config=connections.findByOwnerIdAndSlug(AccountScope.userId(),slug).orElseThrow(()->new BusinessRuleException("Integração não configurada"));
        synchronized(locks.computeIfAbsent(config.getId(),ignored->new Object())){
            var now=OffsetDateTime.now(clock);var last=probes.findFirstByConnectionIdOrderByCheckedAtDescIdDesc(config.getId()).orElse(null);
            boolean cached=last!=null&&!last.getCheckedAt().isAfter(now)&&last.getCheckedAt().plus(properties.checkCooldown()).isAfter(now)&&(config.getUpdatedAt()==null||!last.getCheckedAt().isBefore(config.getUpdatedAt()));
            if(!cached){MonitoredSystem target=new MonitoredSystem();target.setBaseUrl(config.getBaseUrl());target.setHealthEndpoint(config.getHealthEndpoint());target.setTimeoutMs(config.getTimeoutMs());
                last=recorder.record(config,client.probe(target,cipher.decrypt(config.getEncryptedToken())));}
            return new IntegrationCheckResponse(slug,last.isSuccess()?"ONLINE":"OFFLINE",last.getResponseTimeMs(),last.getCheckedAt(),last.getMessage(),cached,last.getCheckedAt().plus(properties.checkCooldown()));
        }
    }
}
