package com.pulseops.exception;

/**
 * Raised when a monitored URL violates the outbound request security policy.
 */
public class UnsafeMonitoredUrlException extends BusinessRuleException {

    public UnsafeMonitoredUrlException(String message) {
        super(message);
    }

    public UnsafeMonitoredUrlException(String message, Throwable cause) {
        super(message);
        initCause(cause);
    }
}
