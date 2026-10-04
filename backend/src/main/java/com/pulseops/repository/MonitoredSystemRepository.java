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

    @Override
    @Query("select system from MonitoredSystem system where " + com.pulseops.security.AccountScope.SYSTEM)
    List<MonitoredSystem> findAll();

    @Override
    @Query("select system from MonitoredSystem system where system.id = :id and " + com.pulseops.security.AccountScope.SYSTEM)
    Optional<MonitoredSystem> findById(@Param("id") UUID id);

    @Override
    @Query("select (count(system) > 0) from MonitoredSystem system where system.id = :id and " + com.pulseops.security.AccountScope.SYSTEM)
    boolean existsById(@Param("id") UUID id);

    @Query("select system from MonitoredSystem system where lower(system.name) = lower(:name) and " + com.pulseops.security.AccountScope.SYSTEM)
    Optional<MonitoredSystem> findByNameIgnoreCase(@Param("name") String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select system from MonitoredSystem system where system.id = :id and " + com.pulseops.security.AccountScope.SYSTEM)
    Optional<MonitoredSystem> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByNameIgnoreCase(String name);

    @Query("select system from MonitoredSystem system where system.active = true and " + com.pulseops.security.AccountScope.SYSTEM + " order by system.name")
    List<MonitoredSystem> findAllByActiveTrueOrderByNameAsc();

    List<MonitoredSystem> findAllByEnvironmentAndActiveTrueOrderByNameAsc(Environment environment);

    long countByStatus(SystemStatus status);
}
