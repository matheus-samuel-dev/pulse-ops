package com.pulseops.repository;

import com.pulseops.domain.incident.Incident;
import com.pulseops.domain.incident.IncidentStatus;
import com.pulseops.domain.system.Environment;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {

    Optional<Incident> findFirstByMonitoredSystemIdAndStatusInOrderByStartedAtDesc(
            UUID monitoredSystemId,
            Collection<IncidentStatus> statuses
    );

    List<Incident> findByMonitoredSystemIdAndStatusInOrderByStartedAtDesc(
            UUID monitoredSystemId,
            Collection<IncidentStatus> statuses
    );

    List<Incident> findByMonitoredSystemIdOrderByStartedAtDesc(UUID monitoredSystemId);

    List<Incident> findAllByStatusInOrderByStartedAtDesc(Collection<IncidentStatus> statuses);

    long countByStatusIn(Collection<IncidentStatus> statuses);

    @Query("""
            select count(incident)
            from Incident incident
            join incident.monitoredSystem system
            where system.active = true
              and incident.status in :statuses
              and (:environment is null or system.environment = :environment)
            """)
    long countActiveForOperationalReport(
            @Param("statuses") Collection<IncidentStatus> statuses,
            @Param("environment") Environment environment
    );

    @Query("""
            select count(incident)
            from Incident incident
            join incident.monitoredSystem system
            where system.active = true
              and incident.startedAt between :start and :end
              and (:environment is null or system.environment = :environment)
            """)
    long countOpenedForOperationalReport(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("environment") Environment environment
    );

    @Query("""
            select incident
            from Incident incident
            join fetch incident.monitoredSystem system
            where system.active = true
              and incident.startedAt between :start and :end
              and (:environment is null or system.environment = :environment)
            order by incident.startedAt desc
            """)
    List<Incident> findFeedForOperationalReport(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("environment") Environment environment,
            Pageable pageable
    );
}
