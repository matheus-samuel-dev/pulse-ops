package com.pulseops.service.report;

import com.pulseops.domain.system.Environment;
import com.pulseops.domain.deployment.DeploymentStatus;
import com.pulseops.domain.event.OperationalEvent;
import com.pulseops.domain.quality.TestReport;
import com.pulseops.dto.report.*;
import com.pulseops.repository.OperationalEventRepository;
import com.pulseops.service.OperationalReadModel;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationalReportService {
    private final OperationalReadModel readModel;
    private final OperationalEventRepository events;
    public OperationalReportService(OperationalReadModel readModel,OperationalEventRepository events){this.readModel=readModel;this.events=events;}
    @Transactional(readOnly=true)
    public OperationalReportResponse generate(String period,Environment environment){return generate(period,environment,null,0);}
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public OperationalReportResponse generate(String period,Environment environment,UUID systemId,int page){
        period=OperationalReadModel.normalize(period);
        var data=readModel.read(period,environment,systemId);
        var window=data.window();
        long total=data.checks().size(),success=data.checks().stream().filter(c->c.isSuccess()).count();
        long deployments=data.deployments().size(),successfulDeployments=data.deployments().stream().filter(d->d.getStatus()==DeploymentStatus.SUCCESS).count();
        long tests=data.reports().stream().mapToLong(TestReport::getTotalTests).sum();
        long passed=data.reports().stream().mapToLong(TestReport::getPassedTests).sum();
        OperationalReportKpis kpis=new OperationalReportKpis(data.systems().size(),data.status(com.pulseops.domain.system.SystemStatus.OPERATIONAL),
            data.status(com.pulseops.domain.system.SystemStatus.DEGRADED),data.status(com.pulseops.domain.system.SystemStatus.DOWN),data.status(com.pulseops.domain.system.SystemStatus.UNKNOWN),
            total,success,total-success,data.averageAvailability(),data.activeIncidents(),data.incidents().size(),deployments,successfulDeployments,OperationalReadModel.percentage(successfulDeployments,deployments),
            tests,passed,data.reports().stream().mapToLong(TestReport::getFailedTests).sum(),data.reports().stream().mapToLong(TestReport::getSkippedTests).sum(),
            OperationalReadModel.percentage(passed,tests),data.lineCoverage(),data.branchCoverage());
        var ids=data.systems().stream().map(com.pulseops.domain.system.MonitoredSystem::getId).toList();
        var feed=ids.isEmpty()?org.springframework.data.domain.Page.<OperationalEvent>empty(PageRequest.of(page,30)):events.reportWindow(ids,environment==null?null:environment.name(),window.start(),window.end(),PageRequest.of(page,30));
        return new OperationalReportResponse(period,environment,window.start(),window.end(),kpis,feed.getContent().stream().map(OperationalReportService::event).toList(),feed.getTotalElements(),feed.getTotalPages(),page);
    }
    public static OperationalEventResponse event(OperationalEvent event){
        OperationalEventType type=event.getType().startsWith("INCIDENT")?OperationalEventType.INCIDENT:event.getType().startsWith("DEPLOYMENT")?OperationalEventType.DEPLOYMENT
            :event.getType().startsWith("QUALITY")?OperationalEventType.QUALITY:event.getType().startsWith("INTEGRATION")?OperationalEventType.INTEGRATION
            :event.getType().startsWith("ACCOUNT")?OperationalEventType.ACCOUNT:event.getType().equals("HEALTH_CHECK")?OperationalEventType.HEALTH_CHECK:OperationalEventType.SYSTEM;
        OperationalEventImpact impact=switch(event.getSeverity()){case "SUCCESS"->OperationalEventImpact.SUCCESS;case "ERROR","CRITICAL"->OperationalEventImpact.CRITICAL;case "WARNING"->OperationalEventImpact.WARNING;default->OperationalEventImpact.INFO;};
        return new OperationalEventResponse(event.getId(),type,event.getOccurredAt(),event.getSystemId(),event.getSystemName(),event.getEnvironment()==null?null:Environment.valueOf(event.getEnvironment()),
            event.getTitle(),event.getDescription(),event.getStatus()==null?"RECORDED":event.getStatus(),impact,event.getSource(),event.getResourceId());
    }
}
