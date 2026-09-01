package com.pulseops.client;

import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.monitoring.HealthProbeResult;

/**
 * Port used by the monitoring use case. The WebClient adapter belongs at the edge of the application.
 */
public interface HealthCheckClient {

    HealthProbeResult probe(MonitoredSystem monitoredSystem);
}
