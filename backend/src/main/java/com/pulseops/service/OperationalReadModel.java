package com.pulseops.service;

import com.pulseops.domain.system.*;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.incident.*;
import com.pulseops.domain.deployment.Deployment;
import com.pulseops.domain.quality.TestReport;
import com.pulseops.dto.common.TimeRange;
import com.pulseops.repository.*;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.service.monitoring.AvailabilityService;
import java.math.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Shared scope, window and formulas for dashboard, reports and CI quality. */
@Service
public class OperationalReadModel {
    private final MonitoredSystemRepository systems;
    private final HealthCheckRepository checks;
    private final IncidentRepository incidents;
    private final DeploymentRepository deployments;
    private final TestReportRepository reports;
    private final Clock clock;
    public OperationalReadModel(MonitoredSystemRepository systems,HealthCheckRepository checks,IncidentRepository incidents,
            DeploymentRepository deployments,TestReportRepository reports,Clock clock) {
        this.systems=systems;this.checks=checks;this.incidents=incidents;this.deployments=deployments;this.reports=reports;this.clock=clock;
    }
    public TimeRange window(String period) {
        OffsetDateTime end=OffsetDateTime.now(clock);
        return new TimeRange(end.minus(switch(normalize(period)){case "7d"->Duration.ofDays(7);case "30d"->Duration.ofDays(30);default->Duration.ofDays(1);}),end);
    }
    public static String normalize(String value) {
        String period=value==null||value.isBlank()?"24h":value.toLowerCase(Locale.ROOT);
        if(!List.of("24h","7d","30d").contains(period))throw new BusinessRuleException("Escolha o período 24h, 7d ou 30d");
        return period;
    }
    @Transactional(readOnly=true)
    public Snapshot read(String period,Environment environment,UUID systemId){return read(window(period),environment,systemId);}
    @Transactional(readOnly=true)
    public Snapshot read(TimeRange window,Environment environment,UUID systemId) {
        if(systemId!=null && !systems.existsById(systemId))throw new ResourceNotFoundException("Sistema",systemId);
        List<MonitoredSystem> selected=systems.findAll().stream().filter(s->environment==null||s.getEnvironment()==environment)
                .filter(s->systemId==null||systemId.equals(s.getId())).sorted(Comparator.comparing(MonitoredSystem::getName,String.CASE_INSENSITIVE_ORDER)).toList();
        Set<UUID> ids=selected.stream().map(MonitoredSystem::getId).collect(Collectors.toSet());
        List<HealthCheck> samples=checks.findAllByCheckedAtBetweenOrderByCheckedAtAsc(window.start(),window.end()).stream().filter(c->ids.contains(c.getMonitoredSystem().getId())).toList();
        List<Incident> incidentRecords=incidents.findAll().stream().filter(i->ids.contains(i.getMonitoredSystem().getId())).filter(i->inside(i.getStartedAt(),window)).toList();
        List<Deployment> deploymentRecords=deployments.findAll().stream().filter(d->ids.contains(d.getMonitoredSystem().getId()))
                .filter(d->environment==null||d.getEnvironment()==environment).filter(d->inside(d.getDeployedAt(),window)).toList();
        List<TestReport> quality=latestReports(reports.findAll().stream().filter(r->ids.contains(r.getMonitoredSystem().getId())).filter(r->inside(r.getGeneratedAt(),window)).toList());
        return new Snapshot(window,selected,samples,incidentRecords,deploymentRecords,quality);
    }
    public static boolean inside(OffsetDateTime time,TimeRange window){return time!=null&&!time.isBefore(window.start())&&!time.isAfter(window.end());}
    public static BigDecimal percentage(long successes,long total){return total==0?null:BigDecimal.valueOf(successes).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total),3,RoundingMode.HALF_UP);}
    public static boolean eligible(HealthCheck check){return !List.of("SECURITY_POLICY","UNEXPECTED").contains(check.getFailureType()==null?"LEGACY_UNCLASSIFIED":check.getFailureType());}
    public static BigDecimal availability(List<HealthCheck> checks){var valid=checks.stream().filter(OperationalReadModel::eligible).toList();return valid.size()<AvailabilityService.MINIMUM_CHECKS?null:percentage(valid.stream().filter(HealthCheck::isSuccess).count(),valid.size());}
    public static BigDecimal average(List<BigDecimal> values){List<BigDecimal> measured=values.stream().filter(Objects::nonNull).toList();return measured.isEmpty()?null:measured.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(measured.size()),3,RoundingMode.HALF_UP);}
    public static List<TestReport> latestReports(List<TestReport> reports){
        Comparator<TestReport> order=Comparator.comparing(TestReport::getGeneratedAt).thenComparing(TestReport::getCreatedAt,Comparator.nullsFirst(Comparator.naturalOrder())).thenComparing(TestReport::getId,Comparator.nullsFirst(Comparator.naturalOrder()));
        return reports.stream().collect(Collectors.toMap(r->r.getMonitoredSystem().getId(),Function.identity(),(a,b)->order.compare(a,b)>=0?a:b)).values().stream().sorted(Comparator.comparing(r->r.getMonitoredSystem().getName())).toList();
    }
    public record Snapshot(TimeRange window,List<MonitoredSystem> systems,List<HealthCheck> checks,List<Incident> incidents,List<Deployment> deployments,List<TestReport> reports) {
        public BigDecimal averageAvailability(){Map<UUID,List<HealthCheck>> grouped=checks.stream().collect(Collectors.groupingBy(c->c.getMonitoredSystem().getId()));return average(systems.stream().map(s->availability(grouped.getOrDefault(s.getId(),List.of()))).toList());}
        public long activeIncidents(){return incidents.stream().filter(i->i.getStatus()!=IncidentStatus.RESOLVED).count();}
        public long status(SystemStatus status){return systems.stream().filter(MonitoredSystem::isActive).filter(s->s.getStatus()==status).count();}
        public BigDecimal lineCoverage(){return average(reports.stream().map(TestReport::getLineCoverage).toList());}
        public BigDecimal branchCoverage(){return average(reports.stream().map(TestReport::getBranchCoverage).toList());}
    }
}
