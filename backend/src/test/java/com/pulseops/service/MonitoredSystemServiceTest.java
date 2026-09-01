package com.pulseops.service;

import com.pulseops.domain.system.Environment;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import com.pulseops.dto.system.MonitoredSystemRequest;
import com.pulseops.dto.system.MonitoredSystemResponse;
import com.pulseops.exception.ConflictException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.exception.UnsafeMonitoredUrlException;
import com.pulseops.repository.MonitoredSystemRepository;
import com.pulseops.security.outbound.MonitoredUrlPolicy;
import com.pulseops.security.outbound.ValidatedMonitoredUrl;
import java.net.URI;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MonitoredSystemService")
class MonitoredSystemServiceTest {

    @Mock
    private MonitoredSystemRepository repository;
    @Mock
    private MonitoredUrlPolicy monitoredUrlPolicy;
    @InjectMocks
    private MonitoredSystemService service;

    @BeforeEach
    void setUpUrlPolicy() {
        lenient().when(monitoredUrlPolicy.validate(any(String.class), any(String.class)))
                .thenAnswer(invocation -> validatedUrl(
                        invocation.getArgument(0), invocation.getArgument(1)));
    }

    @Test
    void shouldCreateUnknownSystemWithCanonicalUrlsAndTrimmedFields() {
        MonitoredSystemRequest request = request(
                "  Payments API  ", "  Critical payment service  ",
                "https://payments.example.com/", "actuator/health");
        when(repository.findByNameIgnoreCase("Payments API")).thenReturn(Optional.empty());
        when(repository.save(any(MonitoredSystem.class))).thenAnswer(invocation -> {
            MonitoredSystem saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        MonitoredSystemResponse response = service.create(request);

        ArgumentCaptor<MonitoredSystem> captor = ArgumentCaptor.forClass(MonitoredSystem.class);
        verify(repository).save(captor.capture());
        MonitoredSystem saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Payments API");
        assertThat(saved.getDescription()).isEqualTo("Critical payment service");
        assertThat(saved.getBaseUrl()).isEqualTo("https://payments.example.com");
        assertThat(saved.getHealthEndpoint()).isEqualTo("/actuator/health");
        assertThat(saved.getStatus()).isEqualTo(SystemStatus.UNKNOWN);
        assertThat(saved.getEnvironment()).isEqualTo(Environment.PRODUCTION);
        assertThat(saved.isActive()).isTrue();
        assertThat(response.id()).isNotNull();
        assertThat(response.targetAvailability()).isEqualByComparingTo("99.900");
    }

    @Test
    void shouldConvertBlankDescriptionToNullAndPreserveCanonicalEndpoint() {
        MonitoredSystemRequest request = request(
                "Catalog", "   ", "https://catalog.example.com", "/health");
        when(repository.findByNameIgnoreCase("Catalog")).thenReturn(Optional.empty());
        when(repository.save(any(MonitoredSystem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MonitoredSystemResponse response = service.create(request);

        assertThat(response.description()).isNull();
        assertThat(response.baseUrl()).isEqualTo("https://catalog.example.com");
        assertThat(response.healthEndpoint()).isEqualTo("/health");
    }

    @Test
    void shouldRejectDuplicateNameBeforeConstructingPersistenceMutation() {
        MonitoredSystem existing = system(UUID.randomUUID(), "Payments API", SystemStatus.OPERATIONAL);
        when(repository.findByNameIgnoreCase("Payments API")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(request(
                " Payments API ", null, "https://payments.example.com", "/health")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("sistema monitorado");

        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectUnsafeUrlBeforePersistenceMutation() {
        MonitoredSystemRequest request = request(
                "Metadata probe", null, "http://169.254.169.254", "/latest/meta-data");
        when(repository.findByNameIgnoreCase("Metadata probe")).thenReturn(Optional.empty());
        when(monitoredUrlPolicy.validate(request.baseUrl(), request.healthEndpoint()))
                .thenThrow(new UnsafeMonitoredUrlException("Destinos de metadata não são permitidos"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(UnsafeMonitoredUrlException.class)
                .hasMessageContaining("metadata");

        verify(repository, never()).save(any());
    }

    @Test
    void shouldUpdateExistingSystemWhenNameStillBelongsToIt() {
        UUID id = UUID.randomUUID();
        MonitoredSystem system = system(id, "Catalog", SystemStatus.DEGRADED);
        when(repository.findById(id)).thenReturn(Optional.of(system));
        when(repository.findByNameIgnoreCase("Catalog API")).thenReturn(Optional.of(system));
        when(repository.save(system)).thenReturn(system);

        MonitoredSystemResponse response = service.update(id, request(
                " Catalog API ", " New description ", "https://catalog.example.com/", "ready"));

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.name()).isEqualTo("Catalog API");
        assertThat(response.description()).isEqualTo("New description");
        assertThat(response.healthEndpoint()).isEqualTo("/ready");
        assertThat(response.status()).isEqualTo(SystemStatus.DEGRADED);
        verify(repository).save(system);
    }

    @Test
    void shouldRejectUpdateWhenNameBelongsToAnotherSystemWithoutMutatingCurrentSystem() {
        UUID id = UUID.randomUUID();
        MonitoredSystem current = system(id, "Catalog", SystemStatus.OPERATIONAL);
        MonitoredSystem owner = system(UUID.randomUUID(), "Payments", SystemStatus.OPERATIONAL);
        when(repository.findById(id)).thenReturn(Optional.of(current));
        when(repository.findByNameIgnoreCase("Payments")).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.update(id, request(
                "Payments", "Changed", "https://changed.example.com", "/health")))
                .isInstanceOf(ConflictException.class);

        assertThat(current.getName()).isEqualTo("Catalog");
        assertThat(current.getBaseUrl()).isEqualTo("https://catalog.example.com");
        verify(repository, never()).save(any());
    }

    @Test
    void shouldNotMutateExistingSystemWhenUpdatedUrlIsBlocked() {
        UUID id = UUID.randomUUID();
        MonitoredSystem current = system(id, "Catalog", SystemStatus.OPERATIONAL);
        MonitoredSystemRequest request = request(
                "Catalog", "Changed", "http://127.0.0.1:8080", "/actuator/health");
        when(repository.findById(id)).thenReturn(Optional.of(current));
        when(repository.findByNameIgnoreCase("Catalog")).thenReturn(Optional.of(current));
        when(monitoredUrlPolicy.validate(request.baseUrl(), request.healthEndpoint()))
                .thenThrow(new UnsafeMonitoredUrlException("Redes privadas estão bloqueadas"));

        assertThatThrownBy(() -> service.update(id, request))
                .isInstanceOf(UnsafeMonitoredUrlException.class)
                .hasMessageContaining("privadas");

        assertThat(current.getDescription()).isEqualTo("Original description");
        assertThat(current.getBaseUrl()).isEqualTo("https://catalog.example.com");
        verify(repository, never()).save(any());
    }

    @Test
    void shouldSortSystemsCaseInsensitivelyBeforeMappingResponses() {
        MonitoredSystem zulu = system(UUID.randomUUID(), "zulu", SystemStatus.OPERATIONAL);
        MonitoredSystem alpha = system(UUID.randomUUID(), "Alpha", SystemStatus.DOWN);
        MonitoredSystem beta = system(UUID.randomUUID(), "beta", SystemStatus.DEGRADED);
        when(repository.findAll()).thenReturn(List.of(zulu, beta, alpha));

        List<MonitoredSystemResponse> result = service.findAll();

        assertThat(result).extracting(MonitoredSystemResponse::name)
                .containsExactly("Alpha", "beta", "zulu");
        verify(repository).findAll();
    }

    @Test
    void shouldFindAndDeleteExistingSystem() {
        UUID id = UUID.randomUUID();
        MonitoredSystem system = system(id, "Catalog", SystemStatus.OPERATIONAL);
        when(repository.findById(id)).thenReturn(Optional.of(system));

        assertThat(service.findById(id).id()).isEqualTo(id);
        service.delete(id);

        verify(repository).delete(system);
    }

    @Test
    void shouldRejectUnknownSystemAndNeverDelete() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findEntity(id))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining(id.toString());
        assertThatThrownBy(() -> service.delete(id))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(repository, never()).delete(any());
    }

    private MonitoredSystemRequest request(String name, String description, String baseUrl, String endpoint) {
        return new MonitoredSystemRequest(
                name, description, baseUrl, endpoint, Environment.PRODUCTION, true,
                200, 2500, 800, new BigDecimal("99.900"));
    }

    private MonitoredSystem system(UUID id, String name, SystemStatus status) {
        MonitoredSystem system = new MonitoredSystem();
        system.setId(id);
        system.setName(name);
        system.setDescription("Original description");
        system.setBaseUrl("https://" + name.toLowerCase() + ".example.com");
        system.setHealthEndpoint("/health");
        system.setEnvironment(Environment.PRODUCTION);
        system.setStatus(status);
        system.setActive(true);
        system.setExpectedStatusCode(200);
        system.setTimeoutMs(2500);
        system.setLatencyThresholdMs(800);
        system.setTargetAvailability(new BigDecimal("99.900"));
        return system;
    }

    private ValidatedMonitoredUrl validatedUrl(String rawBaseUrl, String rawEndpoint) {
        String baseUrl = rawBaseUrl.trim();
        while (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String endpoint = rawEndpoint.trim();
        endpoint = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        URI target = URI.create(baseUrl + "/").resolve(endpoint.substring(1));
        return new ValidatedMonitoredUrl(baseUrl, endpoint, target);
    }
}
