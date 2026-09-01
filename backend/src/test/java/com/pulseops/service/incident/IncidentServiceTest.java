package com.pulseops.service.incident;

import com.pulseops.domain.incident.Incident;
import com.pulseops.domain.incident.IncidentSeverity;
import com.pulseops.domain.incident.IncidentStatus;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.incident.CreateIncidentCommand;
import com.pulseops.exception.BusinessRuleException;
import com.pulseops.exception.InvalidStateTransitionException;
import com.pulseops.exception.ResourceNotFoundException;
import com.pulseops.repository.IncidentRepository;
import com.pulseops.repository.MonitoredSystemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IncidentService")
class IncidentServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-27T15:00:00Z");
    private static final OffsetDateTime NOW_OFFSET = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private MonitoredSystemRepository systemRepository;

    private IncidentService incidentService;

    @BeforeEach
    void setUp() {
        incidentService = new IncidentService(
                incidentRepository,
                systemRepository,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Nested
    @DisplayName("creation")
    class Creation {

        @Test
        void shouldCreateManualIncidentWithNormalizedFieldsAndCurrentTime() {
            UUID systemId = UUID.randomUUID();
            MonitoredSystem system = system(systemId);
            when(systemRepository.findById(systemId)).thenReturn(Optional.of(system));
            when(incidentRepository.save(any(Incident.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            CreateIncidentCommand command = new CreateIncidentCommand(
                    "  API unavailable  ", "  Connection failures detected  ",
                    IncidentSeverity.HIGH, null);

            Incident result = incidentService.create(systemId, command);

            ArgumentCaptor<Incident> captor = ArgumentCaptor.forClass(Incident.class);
            verify(incidentRepository).save(captor.capture());
            assertThat(result).isSameAs(captor.getValue());
            assertThat(result.getMonitoredSystem()).isSameAs(system);
            assertThat(result.getTitle()).isEqualTo("API unavailable");
            assertThat(result.getDescription()).isEqualTo("Connection failures detected");
            assertThat(result.getSeverity()).isEqualTo(IncidentSeverity.HIGH);
            assertThat(result.getStatus()).isEqualTo(IncidentStatus.OPEN);
            assertThat(result.getStartedAt()).isEqualTo(NOW_OFFSET);
            assertThat(result.isAutomatic()).isFalse();
        }

        @Test
        void shouldCreateAutomaticIncidentAndKeepExplicitStartTime() {
            MonitoredSystem system = system(UUID.randomUUID());
            OffsetDateTime startedAt = NOW_OFFSET.minusMinutes(2);
            when(incidentRepository.save(any(Incident.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            Incident result = incidentService.createAutomatic(
                    system, "Automatic outage", null, IncidentSeverity.CRITICAL, startedAt);

            assertThat(result.isAutomatic()).isTrue();
            assertThat(result.getStartedAt()).isEqualTo(startedAt);
            assertThat(result.getDescription()).isNull();
            verifyNoInteractions(systemRepository);
        }

        @Test
        void shouldRejectBlankTitleAndMissingSeverityWithoutSaving() {
            UUID systemId = UUID.randomUUID();
            when(systemRepository.findById(systemId)).thenReturn(Optional.of(system(systemId)));

            assertThatThrownBy(() -> incidentService.create(
                    systemId,
                    new CreateIncidentCommand("   ", "description", IncidentSeverity.LOW, NOW_OFFSET)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("title");
            assertThatThrownBy(() -> incidentService.create(
                    systemId,
                    new CreateIncidentCommand("Valid", "description", null, NOW_OFFSET)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("severity");

            verify(incidentRepository, never()).save(any());
        }

        @Test
        void shouldRejectFieldsBeyondBusinessLimits() {
            UUID systemId = UUID.randomUUID();
            when(systemRepository.findById(systemId)).thenReturn(Optional.of(system(systemId)));

            assertThatThrownBy(() -> incidentService.create(
                    systemId,
                    new CreateIncidentCommand("x".repeat(181), null, IncidentSeverity.LOW, NOW_OFFSET)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("180");
            assertThatThrownBy(() -> incidentService.create(
                    systemId,
                    new CreateIncidentCommand("Valid", "x".repeat(4_001), IncidentSeverity.LOW, NOW_OFFSET)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("4000");

            verify(incidentRepository, never()).save(any());
        }

        @Test
        void shouldNotSaveWhenSystemDoesNotExist() {
            UUID systemId = UUID.randomUUID();
            when(systemRepository.findById(systemId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> incidentService.create(
                    systemId,
                    new CreateIncidentCommand("Outage", null, IncidentSeverity.HIGH, null)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(systemId.toString());

            verify(incidentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("state transitions")
    class StateTransitions {

        @Test
        void shouldMarkOpenIncidentAsInvestigating() {
            UUID incidentId = UUID.randomUUID();
            Incident incident = incident(incidentId, IncidentStatus.OPEN, NOW_OFFSET.minusHours(1));
            when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));
            when(incidentRepository.save(incident)).thenReturn(incident);

            Incident result = incidentService.markInvestigating(incidentId);

            assertThat(result.getStatus()).isEqualTo(IncidentStatus.INVESTIGATING);
            verify(incidentRepository).save(incident);
        }

        @Test
        void shouldRejectInvestigatingTransitionFromAnyNonOpenState() {
            UUID incidentId = UUID.randomUUID();
            Incident resolved = incident(incidentId, IncidentStatus.RESOLVED, NOW_OFFSET.minusHours(1));
            when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(resolved));

            assertThatThrownBy(() -> incidentService.markInvestigating(incidentId))
                    .isInstanceOf(InvalidStateTransitionException.class)
                    .hasMessageContaining("RESOLVED", "INVESTIGATING");

            verify(incidentRepository, never()).save(any());
        }

        @Test
        void shouldResolveIncidentAtClockTime() {
            UUID incidentId = UUID.randomUUID();
            Incident incident = incident(incidentId, IncidentStatus.INVESTIGATING, NOW_OFFSET.minusHours(1));
            when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));
            when(incidentRepository.save(incident)).thenReturn(incident);

            Incident result = incidentService.resolve(incidentId);

            assertThat(result.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
            assertThat(result.getResolvedAt()).isEqualTo(NOW_OFFSET);
            verify(incidentRepository).save(incident);
        }

        @Test
        void shouldRejectDuplicateResolutionAndResolutionBeforeIncidentStart() {
            Incident alreadyResolved = incident(
                    UUID.randomUUID(), IncidentStatus.RESOLVED, NOW_OFFSET.minusHours(1));
            Incident futureIncident = incident(
                    UUID.randomUUID(), IncidentStatus.OPEN, NOW_OFFSET.plusMinutes(1));

            assertThatThrownBy(() -> incidentService.resolve(alreadyResolved, NOW_OFFSET))
                    .isInstanceOf(InvalidStateTransitionException.class);
            assertThatThrownBy(() -> incidentService.resolve(futureIncident, NOW_OFFSET))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("before it started");

            verify(incidentRepository, never()).save(any());
        }

        @Test
        void shouldFailWhenIncidentDoesNotExist() {
            UUID incidentId = UUID.randomUUID();
            when(incidentRepository.findById(incidentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> incidentService.resolve(incidentId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(incidentId.toString());

            verify(incidentRepository, never()).save(any());
        }
    }

    @Test
    void shouldValidateSystemBeforeReturningItsIncidents() {
        UUID systemId = UUID.randomUUID();
        MonitoredSystem system = system(systemId);
        List<Incident> incidents = List.of(incident(UUID.randomUUID(), IncidentStatus.OPEN, NOW_OFFSET));
        when(systemRepository.findById(systemId)).thenReturn(Optional.of(system));
        when(incidentRepository.findByMonitoredSystemIdOrderByStartedAtDesc(systemId))
                .thenReturn(incidents);

        assertThat(incidentService.findBySystem(systemId)).containsExactlyElementsOf(incidents);

        verify(systemRepository).findById(systemId);
        verify(incidentRepository).findByMonitoredSystemIdOrderByStartedAtDesc(systemId);
    }

    private MonitoredSystem system(UUID id) {
        MonitoredSystem system = new MonitoredSystem();
        system.setId(id);
        system.setName("PlaySpace");
        return system;
    }

    private Incident incident(UUID id, IncidentStatus status, OffsetDateTime startedAt) {
        Incident incident = new Incident();
        incident.setId(id);
        incident.setStatus(status);
        incident.setStartedAt(startedAt);
        return incident;
    }
}
