package com.pulseops.controller;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RuntimeSettingsController {
    private final MonitoringConfiguration configuration;
    public RuntimeSettingsController(@Value("${pulseops.monitoring.enabled:true}") boolean enabled,
            @Value("${pulseops.monitoring.fixed-delay-ms:60000}") long intervalMs,
            @Value("${pulseops.monitoring.incident.failures-to-open:3}") int failuresToOpen,
            @Value("${pulseops.monitoring.incident.successes-to-resolve:2}") int successesToResolve) {
        configuration = new MonitoringConfiguration(enabled,intervalMs,failuresToOpen,successesToResolve);
    }
    @GetMapping("/api/settings/monitoring")
    public MonitoringConfiguration get(){return configuration;}
    public record MonitoringConfiguration(boolean enabled,long intervalMs,int failuresToOpen,int successesToResolve) { }
}
