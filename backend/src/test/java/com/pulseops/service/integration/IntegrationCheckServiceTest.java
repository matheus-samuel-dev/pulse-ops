package com.pulseops.service.integration;
import com.pulseops.client.*;
import com.pulseops.domain.integration.*;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.repository.*;
import com.pulseops.security.*;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
class IntegrationCheckServiceTest {
 final IntegrationConnectionRepository connections=mock(IntegrationConnectionRepository.class);
 final IntegrationProbeRepository probes=mock(IntegrationProbeRepository.class);
 final HealthCheckClient client=mock(HealthCheckClient.class);
 final IntegrationCredentialCipher cipher=mock(IntegrationCredentialCipher.class);
 final IntegrationProbeRecorder recorder=mock(IntegrationProbeRecorder.class);
 final IntegrationCheckService service=new IntegrationCheckService(connections,probes,client,cipher,recorder,IntegrationServiceTest.properties(Map.of()),IntegrationServiceTest.CLOCK);
 IntegrationConnection connection;
 @BeforeEach void setup(){SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(new PulseOpsPrincipal(IntegrationServiceTest.OWNER,"Operator","test@test.org","","DEVELOPER"),null));connection=new IntegrationConnection();ReflectionTestUtils.setField(connection,"id",UUID.randomUUID());connection.setOwnerId(IntegrationServiceTest.OWNER);connection.setSlug("ai-web-auditor");connection.setBaseUrl("https://auditor.example.org");connection.setHealthEndpoint("/ready");connection.setTimeoutMs(1234);}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 IntegrationProbe sample(boolean success,OffsetDateTime time){var p=new IntegrationProbe();p.setSuccess(success);p.setCheckedAt(time);p.setMessage("Resposta HTTP");p.setResponseTimeMs(success?123L:null);return p;}
 void configured(){when(connections.findByOwnerIdAndSlug(IntegrationServiceTest.OWNER,connection.getSlug())).thenReturn(Optional.of(connection));}
 @Test void missingConfigurationCannotProbeApplications(){assertThatThrownBy(()->service.check(connection.getSlug())).isInstanceOf(com.pulseops.exception.BusinessRuleException.class);verifyNoInteractions(client,recorder);}
 @Test void executesAndPersistsAnIndependentConnectionProbe(){configured();var result=sample(true,IntegrationServiceTest.NOW);when(recorder.record(eq(connection),any())).thenReturn(result);var r=service.check(connection.getSlug());assertThat(r.cached()).isFalse();assertThat(r.status()).isEqualTo("ONLINE");verify(client).probe(argThat(target->target.getId()==null&&target.getBaseUrl().equals(connection.getBaseUrl())&&target.getHealthEndpoint().equals("/ready")&&target.getTimeoutMs()==1234),isNull());verify(recorder).record(eq(connection),any());}
 @Test void cooldownReusesEvidenceWithoutRepeatingNetworkCalls(){configured();when(probes.findFirstByConnectionIdOrderByCheckedAtDescIdDesc(connection.getId())).thenReturn(Optional.of(sample(true,IntegrationServiceTest.NOW.minusSeconds(5))));assertThat(service.check(connection.getSlug()).cached()).isTrue();verifyNoInteractions(client,recorder);}
 @Test void editedConfigurationCannotReuseOlderResult(){configured();ReflectionTestUtils.setField(connection,"updatedAt",IntegrationServiceTest.NOW);when(probes.findFirstByConnectionIdOrderByCheckedAtDescIdDesc(connection.getId())).thenReturn(Optional.of(sample(true,IntegrationServiceTest.NOW.minusSeconds(5))));when(recorder.record(eq(connection),any())).thenReturn(sample(false,IntegrationServiceTest.NOW));var r=service.check(connection.getSlug());assertThat(r.cached()).isFalse();assertThat(r.responseTimeMs()).isNull();assertThat(r.status()).isEqualTo("OFFLINE");}
 @Test void futureResultCannotBeReused(){configured();when(probes.findFirstByConnectionIdOrderByCheckedAtDescIdDesc(connection.getId())).thenReturn(Optional.of(sample(true,IntegrationServiceTest.NOW.plusSeconds(5))));when(recorder.record(eq(connection),any())).thenReturn(sample(true,IntegrationServiceTest.NOW));assertThat(service.check(connection.getSlug()).cached()).isFalse();}
}
