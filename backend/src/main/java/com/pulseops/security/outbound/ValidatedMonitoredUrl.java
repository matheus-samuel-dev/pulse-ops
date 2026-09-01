package com.pulseops.security.outbound;

import java.net.URI;

/**
 * Canonical persisted values plus the already security-checked outbound target.
 */
public record ValidatedMonitoredUrl(
        String baseUrl,
        String healthEndpoint,
        URI targetUri
) {
}
