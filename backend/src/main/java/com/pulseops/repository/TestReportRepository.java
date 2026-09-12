package com.pulseops.repository;

import com.pulseops.domain.quality.TestReport;
import com.pulseops.domain.system.Environment;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TestReportRepository extends JpaRepository<TestReport, UUID> {

    Optional<TestReport> findFirstByMonitoredSystemIdOrderByCreatedAtDesc(UUID systemId);

    @EntityGraph(attributePaths = "monitoredSystem")
    List<TestReport> findByMonitoredSystemIdInAndCreatedAtBetweenOrderByCreatedAtDesc(
            List<UUID> systemIds, OffsetDateTime start, OffsetDateTime end, Pageable pageable);

    long countByMonitoredSystemIdInAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(
            List<UUID> systemIds, OffsetDateTime start, OffsetDateTime end);

    Optional<TestReport> findFirstByMonitoredSystemIdOrderByGeneratedAtDesc(UUID monitoredSystemId);

    List<TestReport> findByMonitoredSystemIdOrderByGeneratedAtDesc(UUID monitoredSystemId);

    @EntityGraph(attributePaths = "monitoredSystem")
    @Query("""
            select report
            from TestReport report
            join report.monitoredSystem system
            where system.active = true
            order by system.name asc, report.generatedAt desc
            """)
    List<TestReport> findAllForActiveSystemsOrderedBySystemAndGeneratedAt();

    @EntityGraph(attributePaths = "monitoredSystem")
    List<TestReport> findAllByMonitoredSystem_ActiveTrueAndGeneratedAtBetweenOrderByGeneratedAtAsc(
            OffsetDateTime start,
            OffsetDateTime end
    );

    @EntityGraph(attributePaths = "monitoredSystem")
    List<TestReport> findAllByMonitoredSystem_ActiveTrueOrderByGeneratedAtAsc();

    @EntityGraph(attributePaths = "monitoredSystem")
    List<TestReport> findByMonitoredSystemIdAndGeneratedAtBetweenOrderByGeneratedAtAsc(
            UUID monitoredSystemId,
            OffsetDateTime start,
            OffsetDateTime end
    );

    @EntityGraph(attributePaths = "monitoredSystem")
    List<TestReport> findByMonitoredSystemIdOrderByGeneratedAtAsc(UUID monitoredSystemId);

    @Query("""
            select report
            from TestReport report
            join fetch report.monitoredSystem system
            where system.active = true
              and report.generatedAt <= :end
              and report.generatedAt = (
                  select max(candidate.generatedAt)
                  from TestReport candidate
                  where candidate.monitoredSystem = system
                    and candidate.generatedAt <= :end
              )
              and (:environment is null or system.environment = :environment)
            order by system.name asc
            """)
    List<TestReport> findLatestForOperationalReport(
            @Param("end") OffsetDateTime end,
            @Param("environment") Environment environment
    );

    @Query("""
            select report
            from TestReport report
            join fetch report.monitoredSystem system
            where system.active = true
              and report.generatedAt between :start and :end
              and (:environment is null or system.environment = :environment)
            order by report.generatedAt desc
            """)
    List<TestReport> findFeedForOperationalReport(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("environment") Environment environment,
            Pageable pageable
    );
}
