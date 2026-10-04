package com.pulseops.repository;
import com.pulseops.domain.integration.IntegrationRun;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface IntegrationRunRepository extends JpaRepository<IntegrationRun,UUID> {
    Optional<IntegrationRun> findFirstByOwnerIdAndSlugAndHttpStatusBetweenOrderByUpdatedAtDesc(UUID ownerId, String slug, int minimum, int maximum);
    Optional<IntegrationRun> findFirstByOwnerIdAndSlugAndStateOrderByUpdatedAtDesc(UUID ownerId, String slug, String state);
    List<IntegrationRun> findTop10ByStateAndEventIdIsNotNullOrderByCreatedAtAsc(String state);
    Optional<IntegrationRun> findByIdAndOwnerId(UUID id,UUID ownerId);
    @Query("select run from IntegrationRun run where run.ownerId = :ownerId and (:systemId is null or run.systemId = :systemId) order by run.createdAt desc")
    Page<IntegrationRun> list(@Param("ownerId") UUID ownerId,@Param("systemId") UUID systemId,Pageable page);
}
