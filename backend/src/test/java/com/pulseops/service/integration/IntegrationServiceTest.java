package com.pulseops.service.integration;
import com.pulseops.config.IntegrationProperties;
import com.pulseops.domain.integration.*;
import com.pulseops.repository.*;
import com.pulseops.security.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class IntegrationServiceTest {
 static final Clock CLOCK=Clock.fixed(Instant.parse("2026-09-10T14:00:00Z"),ZoneOffset.UTC);
 static final OffsetDateTime NOW=OffsetDateTime.now(CLOCK);
 static final UUID OWNER=UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
 static com.pulseops.domain.system.MonitoredSystem system(){var s=new com.pulseops.domain.system.MonitoredSystem();s.setId(UUID.randomUUID());s.setBaseUrl("https://example.org");s.setActive(true);return s;}
 static IntegrationProperties properties(Map<String,IntegrationProperties.Binding> ignored){return new IntegrationProperties(Duration.ofMinutes(5),Duration.ofMinutes(1),ZoneOffset.UTC,Map.of());}
 final IntegrationConnectionRepository connections=mock(IntegrationConnectionRepository.class);
 final IntegrationProbeRepository probes=mock(IntegrationProbeRepository.class);
 final IntegrationRunRepository runs=mock(IntegrationRunRepository.class);
 final OperationalEventRepository events=mock(OperationalEventRepository.class);
 final IntegrationService service=new IntegrationService(connections,probes,runs,events,properties(Map.of()),new DemoModeProperties(false),CLOCK);
 @BeforeEach void owner(){SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(new PulseOpsPrincipal(OWNER,"Operator","operator@test.org","","DEVELOPER"),null));}
 @AfterEach void clear(){SecurityContextHolder.clearContext();}
 IntegrationConnection config(){var c=new IntegrationConnection();ReflectionTestUtils.setField(c,"id",UUID.randomUUID());c.setOwnerId(OWNER);c.setSlug("ai-web-auditor");c.setBaseUrl("https://auditor.example.org");c.setHealthEndpoint("/health");when(connections.findByOwnerIdAndSlug(OWNER,c.getSlug())).thenReturn(Optional.of(c));return c;}
 IntegrationProbe probe(IntegrationConnection c,boolean success,OffsetDateTime checkedAt){var p=new IntegrationProbe();p.setConnectionId(c.getId());p.setOwnerId(OWNER);p.setSlug(c.getSlug());p.setCheckedAt(checkedAt);p.setSuccess(success);p.setResponseTimeMs(176L);p.setMessage(success?"HTTP 200":"HTTP 503");when(probes.findFirstByConnectionIdOrderByCheckedAtDescIdDesc(c.getId())).thenReturn(Optional.of(p));return p;}
 @Test void onlySpecializedConnectorsAreOffered(){var r=service.overview();assertThat(r.integrations()).extracting(x->x.id()).containsExactly("ai-web-auditor","nexus-flow");assertThat(r.integrations()).allSatisfy(x->{assertThat(x.configured()).isFalse();assertThat(x.systemId()).isNull();assertThat(x.status()).isEqualTo("NOT_CONFIGURED");assertThat(x.healthPercent()).isNull();});assertThat(r.summary().connected()).isZero();}
 @Test void configuredConnectorNeedsOwnProbe(){config();assertThat(service.detail("ai-web-auditor").status()).isEqualTo("UNKNOWN");}
 @Test void recentOwnProbeShowsRealState(){var c=config();var p=probe(c,true,NOW.minusSeconds(20));when(probes.findByConnectionIdAndCheckedAtBetweenOrderByCheckedAtDesc(c.getId(),NOW.minusDays(1),NOW)).thenReturn(List.of(p));var r=service.detail(c.getSlug());assertThat(r.status()).isEqualTo("ONLINE");assertThat(r.responseTimeMs()).isEqualTo(176);assertThat(r.lastSuccessfulSyncAt()).isEqualTo(p.getCheckedAt());assertThat(r.checksLast24h()).isEqualTo(1);assertThat(r.healthPercent()).isNull();assertThat(r.systemId()).isNull();}
 @Test void failedProbeDoesNotInventLastSuccessfulCommunication(){var c=config();probe(c,false,NOW.minusSeconds(20));var r=service.detail(c.getSlug());assertThat(r.status()).isEqualTo("OFFLINE");assertThat(r.lastSuccessfulSyncAt()).isNull();assertThat(r.lastFailureAt()).isEqualTo(NOW.minusSeconds(20));}
 @ParameterizedTest @ValueSource(ints={-600,60}) void staleAndFutureEvidenceIsUnknown(int seconds){var c=config();probe(c,true,NOW.plusSeconds(seconds));assertThat(service.detail(c.getSlug()).status()).isEqualTo("UNKNOWN");}
 @Test void configurationEditInvalidatesEarlierProbe(){var c=config();ReflectionTestUtils.setField(c,"updatedAt",NOW.minusSeconds(5));probe(c,true,NOW.minusSeconds(10));assertThat(service.detail(c.getSlug()).status()).isEqualTo("UNKNOWN");}
 @Test void fiveOwnProbesCalculateConnectorAvailability(){var c=config();var p=probe(c,true,NOW.minusSeconds(20));when(probes.findByConnectionIdAndCheckedAtBetweenOrderByCheckedAtDesc(c.getId(),NOW.minusDays(1),NOW)).thenReturn(Collections.nCopies(5,p));assertThat(service.detail(c.getSlug()).healthPercent()).isEqualByComparingTo("100");}
 @Test void actualRunUpdatesCommunicationAndReportTimes(){var c=config();var run=new IntegrationRun();ReflectionTestUtils.setField(run,"updatedAt",NOW.minusSeconds(10));when(runs.findFirstByOwnerIdAndSlugAndHttpStatusBetweenOrderByUpdatedAtDesc(OWNER,c.getSlug(),200,299)).thenReturn(Optional.of(run));when(runs.findFirstByOwnerIdAndSlugAndStateOrderByUpdatedAtDesc(OWNER,c.getSlug(),"COMPLETED")).thenReturn(Optional.of(run));assertThat(service.detail(c.getSlug()).lastSuccessfulSyncAt()).isEqualTo(run.getUpdatedAt());assertThat(service.detail(c.getSlug()).lastReportAt()).isEqualTo(run.getUpdatedAt());}
 @Test void unknownCatalogRejectsBusinessApplications(){assertThatThrownBy(()->service.detail("arena-predict")).isInstanceOf(com.pulseops.exception.ResourceNotFoundException.class);}
 @ParameterizedTest @NullAndEmptySource @ValueSource(strings={"bad", "file:///etc/passwd", "https://user:secret@example.org", "https://example.org?secret=foo", "https://example.org#fragment", "https://example.org:0", "https://example.org:65536"}) void invalidPublicOriginsAreNotExposed(String value){assertThat(IntegrationService.publicOrigin(value)).isNull();}
 @Test void publicOriginStripsPaths(){assertThat(IntegrationService.publicOrigin("https://example.org/path")).isEqualTo("https://example.org");}
}
