package com.pulseops.config;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Only operators can bind catalogue entries; probe URLs remain in MonitoredSystem. */
@ConfigurationProperties("pulseops.integrations")
public record IntegrationProperties(
        @DefaultValue("PT5M") Duration staleAfter,
        @DefaultValue("PT1M") Duration checkCooldown,
        @DefaultValue("America/Sao_Paulo") ZoneId reportingZone,
        Map<String, Binding> systems
) {
    public IntegrationProperties {
        if (staleAfter == null || staleAfter.isNegative() || staleAfter.isZero()
                || checkCooldown == null || checkCooldown.compareTo(Duration.ofSeconds(30)) < 0) {
            throw new IllegalArgumentException("Integration freshness must be positive and cooldown at least 30 seconds");
        }
        systems = systems == null ? Map.of() : Map.copyOf(systems);
    }

    public Binding binding(String slug) { return systems.getOrDefault(slug, new Binding(null, null)); }

    public record Binding(UUID systemId, String publicUrl) { }
}
