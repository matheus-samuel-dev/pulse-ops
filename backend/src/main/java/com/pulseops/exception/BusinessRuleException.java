package com.pulseops.exception;

/**
 * Base exception for a violated domain invariant or an invalid business operation.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
