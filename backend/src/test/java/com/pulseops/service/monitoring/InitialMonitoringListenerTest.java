package com.pulseops.service.monitoring;
import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.service.EventRecorder;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class InitialMonitoringListenerTest {
 @Test void actualCommittedCreationSchedulesBackendCheck(){var systems=mock(MonitoredSystemRepository.class);var monitor=mock(MonitoringService.class);UUID id=UUID.randomUUID();var system=new MonitoredSystem();system.setId(id);system.setActive(true);when(systems.findById(id)).thenReturn(Optional.of(system));new InitialMonitoringListener(systems,monitor,Runnable::run).created(new EventRecorder.Recorded(UUID.randomUUID(),id,UUID.randomUUID(),"SYSTEM_CREATED"));verify(monitor).checkSystem(id);}
 @Test void unrelatedEventsCannotStartARecursiveMonitoringLoop(){var systems=mock(MonitoredSystemRepository.class);var monitor=mock(MonitoringService.class);new InitialMonitoringListener(systems,monitor,Runnable::run).created(new EventRecorder.Recorded(null,null,null,"HEALTH_CHECK"));verifyNoInteractions(systems,monitor);}
 @Test void maintenanceOrInactiveSystemsAreNotProbed(){for(boolean maintenance:List.of(false,true)){var systems=mock(MonitoredSystemRepository.class);var monitor=mock(MonitoringService.class);var system=new MonitoredSystem();UUID id=UUID.randomUUID();system.setId(id);system.setActive(maintenance);system.setMaintenance(maintenance);when(systems.findById(id)).thenReturn(Optional.of(system));new InitialMonitoringListener(systems,monitor,Runnable::run).created(new EventRecorder.Recorded(null,id,null,"SYSTEM_UPDATED"));verifyNoInteractions(monitor);}}
 @Test void queueRejectionCannotInvalidateCommittedSave(){var monitor=mock(MonitoringService.class);var listener=new InitialMonitoringListener(mock(MonitoredSystemRepository.class),monitor,task->{throw new RejectedExecutionException();});assertThatCode(()->listener.created(new EventRecorder.Recorded(null,UUID.randomUUID(),null,"SYSTEM_CREATED"))).doesNotThrowAnyException();verifyNoInteractions(monitor);}
}
