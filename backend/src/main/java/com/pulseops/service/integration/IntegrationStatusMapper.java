package com.pulseops.service.integration;

import com.pulseops.domain.monitoring.HealthCheck;
import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.domain.system.SystemStatus;
import java.time.Duration;
import java.time.OffsetDateTime;

public final class IntegrationStatusMapper {
    private IntegrationStatusMapper() { }

    public static String status(MonitoredSystem system, HealthCheck latest, OffsetDateTime now, Duration staleAfter) {
        if (system == null || !system.isActive() || latest == null
                || latest.getCheckedAt().isAfter(now)
                || !latest.getCheckedAt().plus(staleAfter).isAfter(now)) return "UNKNOWN";
        if (!latest.isSuccess()) {
            return latest.getHttpStatus() == null || latest.getHttpStatus() >= 500
                    || system.getStatus() == SystemStatus.DOWN ? "OFFLINE" : "ATTENTION";
        }
        return latest.getResponseTimeMs() > system.getLatencyThresholdMs()
                || system.getStatus() == SystemStatus.DEGRADED ? "ATTENTION" : "ONLINE";
    }

    public static String reason(MonitoredSystem system, HealthCheck latest, String status) {
        if (system == null) return "Não configurado";
        if (!system.isActive()) return "Monitoramento pausado";
        if (latest == null) return "Aguardando primeira verificação";
        return switch (status) {
            case "ONLINE" -> "Serviço respondendo dentro do limite de latência";
            case "ATTENTION" -> "Resposta inesperada, latência elevada ou falhas recentes";
            case "OFFLINE" -> "Falha na última verificação de disponibilidade";
            default -> "Verificação desatualizada; disponibilidade atual desconhecida";
        };
    }

    /** Never expose network exception messages, internal addresses or endpoint query strings. */
    public static String checkMessage(HealthCheck check) {
        if (check.isSuccess()) return "Conexão realizada com sucesso";
        if (check.getHttpStatus() != null) return "Resposta HTTP inesperada: " + check.getHttpStatus();
        return "Falha de comunicação; o serviço não respondeu ao health check";
    }
}
