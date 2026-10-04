package com.pulseops.client;

import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.monitoring.HealthProbeResult;
import com.pulseops.dto.monitoring.ProbeFailureType;
import com.pulseops.exception.UnsafeMonitoredUrlException;
import com.pulseops.security.outbound.MonitoredUrlPolicy;
import com.pulseops.security.outbound.PinnedAddressResolverGroup;
import com.pulseops.security.outbound.ValidatedMonitoredUrl;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeoutException;
import org.springframework.stereotype.Component;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

@Component
public class WebClientHealthCheckClient implements HealthCheckClient {

    private final WebClient.Builder builder;
    private final MonitoredUrlPolicy monitoredUrlPolicy;

    public WebClientHealthCheckClient(WebClient.Builder builder, MonitoredUrlPolicy monitoredUrlPolicy) {
        this.builder = builder.clone();
        this.monitoredUrlPolicy = monitoredUrlPolicy;
    }

    @Override
    public HealthProbeResult probe(MonitoredSystem monitoredSystem) {
        return probe(monitoredSystem,null);
    }
    @Override
    public HealthProbeResult probe(MonitoredSystem monitoredSystem,String bearerToken) {
        long startedAt = System.nanoTime();
        try {
            Integer status = Mono.fromCallable(() -> monitoredUrlPolicy.validate(
                            monitoredSystem.getBaseUrl(), monitoredSystem.getHealthEndpoint()))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(target -> request(target,bearerToken))
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

    private Mono<Integer> request(ValidatedMonitoredUrl target,String bearerToken) {
        PinnedAddressResolverGroup resolver = new PinnedAddressResolverGroup(target);
        HttpClient transport = HttpClient.newConnection().resolver(resolver).followRedirect(false);
        return builder.clone().clientConnector(new ReactorClientHttpConnector(transport)).build()
                .get().uri(target.targetUri()).headers(headers -> {if(bearerToken!=null&&!bearerToken.isBlank())headers.setBearerAuth(bearerToken);})
                .exchangeToMono(response -> Mono.just(response.statusCode().value()))
                .doFinally(signal -> resolver.close());
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
