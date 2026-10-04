package com.pulseops.service.integration;
import com.pulseops.config.IntegrationProperties;
import com.pulseops.domain.integration.*;
import com.pulseops.dto.integration.*;
import com.pulseops.repository.*;
import com.pulseops.security.*;
import java.time.*;
import java.net.URI;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service @Transactional(readOnly=true)
public class IntegrationService {
    private final IntegrationConnectionRepository connections;private final IntegrationProbeRepository probes;private final IntegrationRunRepository runs;
    private final OperationalEventRepository events;private final IntegrationProperties properties;private final DemoModeProperties demo;private final Clock clock;
    public IntegrationService(IntegrationConnectionRepository connections,IntegrationProbeRepository probes,IntegrationRunRepository runs,OperationalEventRepository events,IntegrationProperties properties,DemoModeProperties demo,Clock clock){this.connections=connections;this.probes=probes;this.runs=runs;this.events=events;this.properties=properties;this.demo=demo;this.clock=clock;}
    public IntegrationOverviewResponse overview(){
        var now=OffsetDateTime.now(clock);var entries=IntegrationCatalog.entries().stream().map(e->describe(e,now)).toList();
        return new IntegrationOverviewResponse(entries,new IntegrationOverviewResponse.Summary(entries.stream().filter(IntegrationResponse::configured).count(),entries.stream().filter(e->e.status().equals("ONLINE")).count(),entries.stream().filter(e->List.of("OFFLINE","ATTENTION").contains(e.status())).count(),events.countIntegrationActivity(AccountScope.userId(),now.atZoneSameInstant(properties.reportingZone()).toLocalDate().atStartOfDay(properties.reportingZone()).toOffsetDateTime(),now)),demo.readOnly(),now,properties.reportingZone().getId());
    }
    public IntegrationResponse detail(String slug){return describe(IntegrationCatalog.find(slug),OffsetDateTime.now(clock));}
    private IntegrationResponse describe(IntegrationCatalog catalog,OffsetDateTime now){
        UUID owner=AccountScope.userId();var config=owner==null?null:connections.findByOwnerIdAndSlug(owner,catalog.slug()).orElse(null);
        IntegrationProbe last=config==null?null:probes.findFirstByConnectionIdOrderByCheckedAtDescIdDesc(config.getId()).orElse(null);
        boolean valid=last!=null&&!last.getCheckedAt().isAfter(now)&&(config.getUpdatedAt()==null||!last.getCheckedAt().isBefore(config.getUpdatedAt()));
        boolean fresh=valid&&last.getCheckedAt().plus(properties.staleAfter()).isAfter(now);
        String state=config==null?"NOT_CONFIGURED":!fresh?"UNKNOWN":last.isSuccess()?"ONLINE":"OFFLINE";
        String reason=config==null?"Não configurado":last==null?"Aguardando teste de conexão":!fresh?"Sem verificação válida recente; teste a conexão novamente.":last.getMessage();
        var action=owner==null?Optional.<IntegrationRun>empty():runs.findFirstByOwnerIdAndSlugAndHttpStatusBetweenOrderByUpdatedAtDesc(owner,catalog.slug(),200,299);
        OffsetDateTime synced=action.map(IntegrationRun::getUpdatedAt).orElse(null);
        if(last!=null&&last.isSuccess()&&(synced==null||last.getCheckedAt().isAfter(synced)))synced=last.getCheckedAt();
        var completed=owner==null?null:runs.findFirstByOwnerIdAndSlugAndStateOrderByUpdatedAtDesc(owner,catalog.slug(),"COMPLETED").map(IntegrationRun::getUpdatedAt).orElse(null);
        var samples=config==null?List.<IntegrationProbe>of():probes.findByConnectionIdAndCheckedAtBetweenOrderByCheckedAtDesc(config.getId(),now.minusDays(1),now);
        long success=samples.stream().filter(IntegrationProbe::isSuccess).count();
        return new IntegrationResponse(catalog.slug(),catalog.title(),catalog.description(),catalog.type(),catalog==IntegrationCatalog.AI_WEB_AUDITOR,
            null,config!=null,config!=null,state,reason,config==null?null:config.getPublicUrl(),config==null?null:config.getBaseUrl(),config==null?null:config.getHealthEndpoint(),
            last==null?null:last.getCheckedAt(),synced,completed,last==null?null:last.getResponseTimeMs(),samples.size()<5?null:com.pulseops.service.OperationalReadModel.percentage(success,samples.size()),samples.size(),samples.size()-success,last!=null&&!last.isSuccess()?last.getCheckedAt():null,last==null?null:last.getCheckedAt().plus(properties.checkCooldown()));
    }
    static String publicOrigin(String value){
        if(value==null||value.isBlank())return null;
        try{URI uri=URI.create(value);if((uri.getScheme()==null||!List.of("http","https").contains(uri.getScheme()))||uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||uri.getPort()==0||uri.getPort()>65535)return null;return uri.getScheme()+"://"+uri.getRawAuthority();}catch(IllegalArgumentException e){return null;}
    }
}
