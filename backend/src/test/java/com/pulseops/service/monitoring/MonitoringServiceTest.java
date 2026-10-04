package com.pulseops.service.monitoring;

import com.pulseops.client.HealthCheckClient;
import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.monitoring.HealthProbeResult;
import com.pulseops.dto.monitoring.ProbeFailureType;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.MonitoredSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MonitoringService")
class MonitoringServiceTest {

    @Mock
    private MonitoredSystemRepository systemRepository;
    @Mock
    private HealthCheckClient healthCheckClient;
    @Mock
    private MonitoringPersistenceService persistenceService;

    private MonitoringService monitoringService;

    @BeforeEach
    void setUp() {
        monitoringService = new MonitoringService(systemRepository, healthCheckClient, persistenceService);
    }

    @Test
    void shouldProbeBeforeDelegatingTheAtomicPersistenceStep() {
        UUID systemId = UUID.randomUUID();
        MonitoredSystem target = activeSystem(systemId);
        HealthProbeResult probe = HealthProbeResult.response(200, 245);
        HealthCheck persistedCheck = new HealthCheck();
        when(systemRepository.findById(systemId)).thenReturn(Optional.of(target));
        when(healthCheckClient.probe(target)).thenReturn(probe);
        when(persistenceService.record(target, probe)).thenReturn(persistedCheck);

        HealthCheck result = monitoringService.checkSystem(systemId);

        assertThat(result).isSameAs(persistedCheck);
        InOrder order = inOrder(systemRepository, healthCheckClient, persistenceService);
        order.verify(systemRepository).findById(systemId);
        order.verify(healthCheckClient).probe(target);
        order.verify(persistenceService).record(target, probe);
    }

    @Test
    void shouldConvertClientExceptionIntoUnexpectedProbeBeforePersistence() {
        UUID systemId = UUID.randomUUID();
        MonitoredSystem target = activeSystem(systemId);
        HealthCheck persistedCheck = new HealthCheck();
        when(systemRepository.findById(systemId)).thenReturn(Optional.of(target));
        when(healthCheckClient.probe(target)).thenThrow(new IllegalStateException("client exploded"));
        when(persistenceService.record(same(target), any(HealthProbeResult.class))).thenReturn(persistedCheck);

        HealthCheck result = monitoringService.checkSystem(systemId);

        ArgumentCaptor<HealthProbeResult> probeCaptor = ArgumentCaptor.forClass(HealthProbeResult.class);
        verify(persistenceService).record(same(target), probeCaptor.capture());
        assertThat(result).isSameAs(persistedCheck);
        assertThat(probeCaptor.getValue().failureType()).isEqualTo(ProbeFailureType.UNEXPECTED);
        assertThat(probeCaptor.getValue().errorMessage()).isEqualTo("client exploded");
    }

    @Test
    void shouldConvertNullClientResultIntoUnexpectedProbe() {
        UUID systemId = UUID.randomUUID();
        MonitoredSystem target = activeSystem(systemId);
        when(systemRepository.findById(systemId)).thenReturn(Optional.of(target));
        when(healthCheckClient.probe(target)).thenReturn(null);

        monitoringService.checkSystem(systemId);

        ArgumentCaptor<HealthProbeResult> probeCaptor = ArgumentCaptor.forClass(HealthProbeResult.class);
        verify(persistenceService).record(same(target), probeCaptor.capture());
        assertThat(probeCaptor.getValue().failureType()).isEqualTo(ProbeFailureType.UNEXPECTED);
        assertThat(probeCaptor.getValue().errorMessage()).contains("no result");
    }

    @Test
    void shouldRejectInactiveSystemWithoutCallingExternalClientOrPersistence() {
        UUID systemId = UUID.randomUUID();
        MonitoredSystem target = activeSystem(systemId);
        target.setActive(false);
        when(systemRepository.findById(systemId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> monitoringService.checkSystem(systemId))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Sistemas inativos não podem ser monitorados");

        verifyNoInteractions(healthCheckClient, persistenceService);
    }

    @Test
    void shouldFailFastWhenSystemDoesNotExist() {
        UUID systemId = UUID.randomUUID();
        when(systemRepository.findById(systemId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> monitoringService.checkSystem(systemId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(systemId.toString());

        verifyNoInteractions(healthCheckClient, persistenceService);
    }

    @Test
    void shouldPropagatePersistenceFailureWithoutRepeatingTheExternalProbe() {
        UUID systemId = UUID.randomUUID();
        MonitoredSystem target = activeSystem(systemId);
        HealthProbeResult probe = HealthProbeResult.response(200, 100);
        when(systemRepository.findById(systemId)).thenReturn(Optional.of(target));
        when(healthCheckClient.probe(target)).thenReturn(probe);
        when(persistenceService.record(target, probe)).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> monitoringService.checkSystem(systemId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        verify(healthCheckClient).probe(target);
        verify(persistenceService).record(target, probe);
        verify(healthCheckClient, never()).probe(null);
    }

    private MonitoredSystem activeSystem(UUID id) {
        MonitoredSystem system = new MonitoredSystem();
        system.setId(id);
        system.setName("PlaySpace");
        system.setActive(true);
        system.setBaseUrl("https://playspace.example");
        system.setHealthEndpoint("/actuator/health");
        return system;
    }
}
