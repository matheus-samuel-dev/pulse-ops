package com.pulseops.repository;

import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.system.Environment;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HealthCheckRepository extends JpaRepository<HealthCheck, UUID> {

    java.util.Optional<HealthCheck> findFirstByMonitoredSystemIdOrderByCheckedAtDesc(UUID systemId);

    java.util.Optional<HealthCheck> findFirstByMonitoredSystemIdAndSuccessFalseOrderByCheckedAtDesc(UUID systemId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = "monitoredSystem")
    List<HealthCheck> findByMonitoredSystemIdInAndCheckedAtBetweenOrderByCheckedAtDesc(
            List<UUID> systemIds, OffsetDateTime start, OffsetDateTime end, Pageable pageable);

    long countByMonitoredSystemIdInAndCheckedAtGreaterThanEqualAndCheckedAtLessThan(
            List<UUID> systemIds, OffsetDateTime start, OffsetDateTime end);

    List<HealthCheck> findAllByCheckedAtBetweenOrderByCheckedAtAsc(
            OffsetDateTime start,
            OffsetDateTime end
    );

    List<HealthCheck> findByMonitoredSystemIdAndCheckedAtBetweenOrderByCheckedAtAsc(
            UUID monitoredSystemId,
            OffsetDateTime start,
            OffsetDateTime end
    );

    List<HealthCheck> findTop10ByMonitoredSystemIdOrderByCheckedAtDesc(UUID monitoredSystemId);

    Page<HealthCheck> findByMonitoredSystemIdOrderByCheckedAtDesc(UUID monitoredSystemId, Pageable pageable);

    long countByMonitoredSystemIdAndCheckedAtBetween(
            UUID monitoredSystemId,
            OffsetDateTime start,
            OffsetDateTime end
    );

    long countByMonitoredSystemIdAndSuccessTrueAndCheckedAtBetween(
            UUID monitoredSystemId,
            OffsetDateTime start,
            OffsetDateTime end
    );

    @Query("""
            select count(check)
            from HealthCheck check
            join check.monitoredSystem system
            where system.active = true
              and check.checkedAt between :start and :end
              and (:environment is null or system.environment = :environment)
            """)
    long countForOperationalReport(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("environment") Environment environment
    );

    @Query("""
            select count(check)
            from HealthCheck check
            join check.monitoredSystem system
            where system.active = true
              and check.success = true
              and check.checkedAt between :start and :end
              and (:environment is null or system.environment = :environment)
            """)
    long countSuccessfulForOperationalReport(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("environment") Environment environment
    );

    @Query("""
            select check
            from HealthCheck check
            join fetch check.monitoredSystem system
            where system.active = true
              and check.checkedAt between :start and :end
              and (:environment is null or system.environment = :environment)
            order by check.checkedAt desc
            """)
    List<HealthCheck> findFeedForOperationalReport(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("environment") Environment environment,
            Pageable pageable
    );
}
