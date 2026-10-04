package com.pulseops.exception;

public class InvalidStateTransitionException extends BusinessRuleException {

    public InvalidStateTransitionException(String aggregate, Object from, Object to) {
        super("Transição de %s inválida: %s → %s".formatted(
                "deployment".equalsIgnoreCase(aggregate) ? "deploy" : "incidente", from, to));
    }
}
