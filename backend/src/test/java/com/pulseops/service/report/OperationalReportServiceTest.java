package com.pulseops.service.report;
import com.pulseops.domain.system.*;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.event.OperationalEvent;
import com.pulseops.repository.*;
import com.pulseops.service.OperationalReadModel;
import com.pulseops.dto.common.TimeRange;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class OperationalReportServiceTest {
 @Test void usesSharedSnapshotAndPersistedFeed(){var read=mock(OperationalReadModel.class);var events=mock(OperationalEventRepository.class);var end=OffsetDateTime.parse("2026-10-03T12:00:00Z");var system=new MonitoredSystem();system.setId(UUID.randomUUID());system.setActive(true);system.setStatus(SystemStatus.OPERATIONAL);var check=new HealthCheck();check.setSuccess(true);check.setMonitoredSystem(system);var data=new OperationalReadModel.Snapshot(new TimeRange(end.minusDays(7),end),List.of(system),Collections.nCopies(5,check),List.of(),List.of(),List.of());when(read.read("7d",Environment.PRODUCTION,system.getId())).thenReturn(data);var event=new OperationalEvent();event.setId(UUID.randomUUID());event.setType("HEALTH_CHECK");event.setSeverity("SUCCESS");event.setSource("Monitoramento HTTP");event.setStatus("SUCCESS");event.setOccurredAt(end);when(events.reportWindow(eq(List.of(system.getId())),eq("PRODUCTION"),eq(end.minusDays(7)),eq(end),any())).thenReturn(new PageImpl<>(List.of(event)));var r=new OperationalReportService(read,events).generate("7d",Environment.PRODUCTION,system.getId(),0);assertThat(r.kpis().availability()).isEqualByComparingTo("100");assertThat(r.kpis().totalHealthChecks()).isEqualTo(5);assertThat(r.kpis().averageLineCoverage()).isNull();assertThat(r.feed().getFirst().id()).isEqualTo(event.getId());assertThat(r.feed().getFirst().source()).isEqualTo(event.getSource());assertThat(r.totalEvents()).isOne();}
 @Test void invalidPeriodDoesNotQueryPersistence(){var read=mock(OperationalReadModel.class);var events=mock(OperationalEventRepository.class);assertThatThrownBy(()->new OperationalReportService(read,events).generate("90d",null)).isInstanceOf(com.pulseops.exception.BusinessRuleException.class);verifyNoInteractions(read,events);}
 @Test void persistedEventsRetainOriginAndNullableEnvironment(){for(String type:List.of("SYSTEM_CREATED","ACCOUNT_LOGIN","INCIDENT_OPENED","DEPLOYMENT_FAILED","QUALITY_RECEIVED","INTEGRATION_CHECK","HEALTH_CHECK")){var event=new OperationalEvent();event.setType(type);event.setSeverity("ERROR");event.setSource("Fonte real");var r=OperationalReportService.event(event);assertThat(r.environment()).isNull();assertThat(r.status()).isEqualTo("RECORDED");assertThat(r.impact().name()).isEqualTo("CRITICAL");assertThat(r.source()).isEqualTo("Fonte real");}}
}
