package com.pulseops.repository;

import com.pulseops.domain.system.Environment;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MonitoredSystemRepository extends JpaRepository<MonitoredSystem, UUID> {

    Optional<MonitoredSystem> findByNameIgnoreCase(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select system from MonitoredSystem system where system.id = :id")
    Optional<MonitoredSystem> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByNameIgnoreCase(String name);

    List<MonitoredSystem> findAllByActiveTrueOrderByNameAsc();

    List<MonitoredSystem> findAllByEnvironmentAndActiveTrueOrderByNameAsc(Environment environment);

    long countByStatus(SystemStatus status);
}
