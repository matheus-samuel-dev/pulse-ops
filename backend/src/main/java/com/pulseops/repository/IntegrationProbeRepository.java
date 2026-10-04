package com.pulseops.repository;
import com.pulseops.domain.integration.IntegrationProbe;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface IntegrationProbeRepository extends JpaRepository<IntegrationProbe,UUID> {
    java.util.List<IntegrationProbe> findByConnectionIdAndCheckedAtBetweenOrderByCheckedAtDesc(java.util.UUID connectionId,java.time.OffsetDateTime start,java.time.OffsetDateTime end);
    Optional<IntegrationProbe> findFirstByConnectionIdOrderByCheckedAtDescIdDesc(UUID connectionId);
}
