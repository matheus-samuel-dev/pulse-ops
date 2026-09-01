package com.pulseops.exception;

public class InvalidStateTransitionException extends BusinessRuleException {

    public InvalidStateTransitionException(String aggregate, Object from, Object to) {
        super("Invalid %s state transition from %s to %s".formatted(aggregate, from, to));
    }
}
