package com.pulseops.repository;

import com.pulseops.domain.event.OperationalEvent;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OperationalEventRepository extends JpaRepository<OperationalEvent, UUID> {
    @Query("""
        select event from OperationalEvent event
        where (:#{T(com.pulseops.security.AccountScope).unrestricted()} = true
          or event.ownerId = :#{T(com.pulseops.security.AccountScope).userId()})
        and (:systemId is null or event.systemId = :systemId)
        and (:severity is null or event.severity = :severity)
        and (:environment is null or event.environment = :environment)
        and (cast(:start as timestamp) is null or event.occurredAt >= :start)
        and (cast(:end as timestamp) is null or event.occurredAt <= :end)
        and (lower(event.title) like lower(concat('%', :query, '%'))
          or lower(event.systemName) like lower(concat('%', :query, '%')))
        order by event.occurredAt desc, event.id desc
        """)
    Page<OperationalEvent> searchWindow(@Param("systemId") UUID systemId, @Param("severity") String severity,
            @Param("query") String query,@Param("environment") String environment,@Param("start") java.time.OffsetDateTime start,@Param("end") java.time.OffsetDateTime end, Pageable page);
    @Query("select event from OperationalEvent event where event.systemId in :systemIds and (:#{T(com.pulseops.security.AccountScope).unrestricted()} = true or event.ownerId=:#{T(com.pulseops.security.AccountScope).userId()}) and (:environment is null or event.environment=:environment) and event.occurredAt between :start and :end order by event.occurredAt desc,event.id desc")
    Page<OperationalEvent> reportWindow(@Param("systemIds") java.util.List<UUID> systemIds,@Param("environment") String environment,@Param("start") java.time.OffsetDateTime start,@Param("end") java.time.OffsetDateTime end,Pageable page);
    @Query("select event from OperationalEvent event where (:#{T(com.pulseops.security.AccountScope).unrestricted()} = true or event.ownerId = :#{T(com.pulseops.security.AccountScope).userId()}) and event.type like 'INTEGRATION%' and (:source is null or event.source=:source) and event.occurredAt between :start and :end order by event.occurredAt desc,event.id desc")
    java.util.List<OperationalEvent> integrationActivity(@Param("source") String source,@Param("start") java.time.OffsetDateTime start,@Param("end") java.time.OffsetDateTime end,Pageable page);
    @Query("select count(event) from OperationalEvent event where event.ownerId=:owner and event.type like 'INTEGRATION%' and event.occurredAt between :start and :end")
    long countIntegrationActivity(@Param("owner") UUID owner,@Param("start") java.time.OffsetDateTime start,@Param("end") java.time.OffsetDateTime end);
    default Page<OperationalEvent> search(UUID systemId,String severity,String query,Pageable page){return searchWindow(systemId,severity,query,null,null,null,page);}
}
