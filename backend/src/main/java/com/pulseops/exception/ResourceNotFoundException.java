package com.pulseops.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super("%s não encontrado: %s".formatted(switch (resource) {
            case "Monitored system" -> "Sistema";
            case "Deployment" -> "Deploy";
            case "Incident" -> "Incidente";
            case "Test report" -> "Relatório de testes";
            case "Test report for monitored system" -> "Relatório de testes do sistema";
            case "User" -> "Usuário";
            default -> resource;
        }, identifier));
    }
}
