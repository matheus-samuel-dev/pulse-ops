package com.pulseops.security.outbound;

import java.net.URI;
import java.net.InetAddress;
import java.util.List;

/**
 * Canonical persisted values plus the already security-checked outbound target.
 */
public record ValidatedMonitoredUrl(
        String baseUrl,
        String healthEndpoint,
        URI targetUri,
        List<InetAddress> addresses
) {
    public ValidatedMonitoredUrl { addresses = List.copyOf(addresses); }
}
