package com.pulseops.repository;

import com.pulseops.domain.deployment.Deployment;
import com.pulseops.domain.deployment.DeploymentStatus;
import com.pulseops.domain.system.Environment;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeploymentRepository extends JpaRepository<Deployment, UUID> {

    List<Deployment> findByMonitoredSystemIdOrderByDeployedAtDesc(UUID monitoredSystemId);

    List<Deployment> findAllByDeployedAtBetweenOrderByDeployedAtDesc(
            OffsetDateTime start,
            OffsetDateTime end
    );

    long countByStatusIn(Collection<DeploymentStatus> statuses);

    @Query("""
            select count(deployment)
            from Deployment deployment
            join deployment.monitoredSystem system
            where system.active = true
              and deployment.deployedAt between :start and :end
              and (:environment is null or system.environment = :environment)
            """)
    long countForOperationalReport(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("environment") Environment environment
    );

    @Query("""
            select count(deployment)
            from Deployment deployment
            join deployment.monitoredSystem system
            where system.active = true
              and deployment.status = :status
              and deployment.deployedAt between :start and :end
              and (:environment is null or system.environment = :environment)
            """)
    long countByStatusForOperationalReport(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("environment") Environment environment,
            @Param("status") DeploymentStatus status
    );

    @Query("""
            select deployment
            from Deployment deployment
            join fetch deployment.monitoredSystem system
            where system.active = true
              and deployment.deployedAt between :start and :end
              and (:environment is null or system.environment = :environment)
            order by deployment.deployedAt desc
            """)
    List<Deployment> findFeedForOperationalReport(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end,
            @Param("environment") Environment environment,
            Pageable pageable
    );
}
