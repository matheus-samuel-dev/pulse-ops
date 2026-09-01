package com.pulseops.client;

import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.monitoring.HealthProbeResult;
import com.pulseops.dto.monitoring.ProbeFailureType;
import com.pulseops.exception.UnsafeMonitoredUrlException;
import com.pulseops.security.outbound.MonitoredUrlPolicy;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

@Component
public class WebClientHealthCheckClient implements HealthCheckClient {

    private final WebClient webClient;
    private final MonitoredUrlPolicy monitoredUrlPolicy;

    public WebClientHealthCheckClient(WebClient.Builder builder, MonitoredUrlPolicy monitoredUrlPolicy) {
        this.webClient = builder.build();
        this.monitoredUrlPolicy = monitoredUrlPolicy;
    }

    @Override
    public HealthProbeResult probe(MonitoredSystem monitoredSystem) {
        long startedAt = System.nanoTime();
        try {
            Integer status = webClient.get()
                    .uri(resolveHealthUri(monitoredSystem))
                    .exchangeToMono(response -> Mono.just(response.statusCode().value()))
                    .timeout(Duration.ofMillis(monitoredSystem.getTimeoutMs()))
                    .block();
            long elapsed = elapsedMillis(startedAt);
            if (status == null) {
                return HealthProbeResult.failure(ProbeFailureType.UNEXPECTED, elapsed, "Resposta HTTP vazia");
            }
            return HealthProbeResult.response(status, elapsed);
        } catch (RuntimeException exception) {
            long elapsed = elapsedMillis(startedAt);
            Throwable root = rootCause(exception);
            if (contains(exception, "completed without emitting a response")) {
                return HealthProbeResult.failure(
                        ProbeFailureType.UNEXPECTED, elapsed, "Resposta HTTP vazia");
            }
            ProbeFailureType type = classify(root, exception);
            return HealthProbeResult.failure(type, elapsed, safeMessage(root));
        }
    }

    URI resolveHealthUri(MonitoredSystem system) {
        return monitoredUrlPolicy.validate(system.getBaseUrl(), system.getHealthEndpoint()).targetUri();
    }

    private ProbeFailureType classify(Throwable root, RuntimeException exception) {
        if (root instanceof TimeoutException || contains(exception, "timeout") || contains(exception, "timed out")) {
            return ProbeFailureType.TIMEOUT;
        }
        if (root instanceof UnknownHostException) {
            return ProbeFailureType.DNS;
        }
        if (exception instanceof UnsafeMonitoredUrlException) {
            return ProbeFailureType.SECURITY_POLICY;
        }
        if (root instanceof ConnectException && contains(root, "refused")) {
            return ProbeFailureType.CONNECTION_REFUSED;
        }
        if (exception instanceof WebClientRequestException || root instanceof ConnectException) {
            return ProbeFailureType.NETWORK;
        }
        return ProbeFailureType.UNEXPECTED;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private boolean contains(Throwable throwable, String value) {
        return throwable.getMessage() != null
                && throwable.getMessage().toLowerCase(Locale.ROOT).contains(value);
    }

    private String safeMessage(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }

    private long elapsedMillis(long startedAt) {
        return Math.max(0L, Duration.ofNanos(System.nanoTime() - startedAt).toMillis());
    }
}
