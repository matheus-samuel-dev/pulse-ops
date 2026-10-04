package com.pulseops.repository;
import com.pulseops.domain.integration.IntegrationConnection;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface IntegrationConnectionRepository extends JpaRepository<IntegrationConnection,UUID> {
    Optional<IntegrationConnection> findByOwnerIdAndSlug(UUID ownerId,String slug);
    List<IntegrationConnection> findByOwnerIdAndSlugAndAutoDispatchTrue(UUID ownerId,String slug);
}
