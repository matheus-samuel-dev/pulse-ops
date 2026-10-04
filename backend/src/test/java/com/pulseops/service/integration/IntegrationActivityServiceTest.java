package com.pulseops.service.integration;
import com.pulseops.domain.event.OperationalEvent;
import com.pulseops.repository.OperationalEventRepository;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class IntegrationActivityServiceTest {
 @Test void usesOnlyPersistedIntegrationEvents(){var repository=mock(OperationalEventRepository.class);var event=new OperationalEvent();event.setType("INTEGRATION_CHECK");event.setSeverity("SUCCESS");event.setTitle("Conexão verificada");event.setSource("AI Web Auditor");event.setSystemName("Conta pessoal");when(repository.integrationActivity(isNull(),any(),any(),any())).thenReturn(List.of(event));var response=new IntegrationActivityService(repository,IntegrationServiceTest.CLOCK).events(null);assertThat(response).hasSize(1);assertThat(response.getFirst().source()).isEqualTo(event.getSource());assertThat(response.getFirst().type().name()).isEqualTo("INTEGRATION");}
 @Test void connectorFilterUsesItsRealSource(){var repository=mock(OperationalEventRepository.class);when(repository.integrationActivity(anyString(),any(),any(),any())).thenReturn(List.of());assertThat(new IntegrationActivityService(repository,IntegrationServiceTest.CLOCK).events("nexus-flow")).isEmpty();verify(repository).integrationActivity(eq("Nexus Flow"),any(),any(),any());}
}
