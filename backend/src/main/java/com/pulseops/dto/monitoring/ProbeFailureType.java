package com.pulseops.dto.monitoring;

public enum ProbeFailureType {
    NONE,
    TIMEOUT,
    DNS,
    CONNECTION_REFUSED,
    NETWORK,
    SECURITY_POLICY,
    UNEXPECTED
}
