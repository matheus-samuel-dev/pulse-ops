package com.pulseops.security.outbound;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Security policy for destinations reached by the monitoring HTTP client.
 * Private targets are opt-in so production remains deny-by-default.
 */
@ConfigurationProperties(prefix = "pulseops.security.outbound")
public record OutboundUrlSecurityProperties(boolean allowPrivateNetworks) {
}
