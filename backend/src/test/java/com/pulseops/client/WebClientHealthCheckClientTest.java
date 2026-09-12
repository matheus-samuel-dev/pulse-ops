package com.pulseops.client;

import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.monitoring.HealthProbeResult;
import com.pulseops.dto.monitoring.ProbeFailureType;
import com.pulseops.exception.UnsafeMonitoredUrlException;
import com.pulseops.security.outbound.MonitoredUrlPolicy;
import com.pulseops.security.outbound.ValidatedMonitoredUrl;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.UnknownHostException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebClientHealthCheckClient")
class WebClientHealthCheckClientTest {

    @Mock
    private ExchangeFunction exchangeFunction;
    @Mock
    private MonitoredUrlPolicy monitoredUrlPolicy;

    private WebClientHealthCheckClient client;

    @BeforeEach
    void setUp() {
        client = new WebClientHealthCheckClient(
                WebClient.builder().exchangeFunction(exchangeFunction), monitoredUrlPolicy);
        lenient().when(monitoredUrlPolicy.validate(any(String.class), any(String.class)))
                .thenAnswer(invocation -> validatedUrl(
                        invocation.getArgument(0), invocation.getArgument(1)));
    }

    @Test
    void shouldCallResolvedHealthUriAndReturnAnyHttpResponseAsReachedServer() {
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.just(
                ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build()));
        MonitoredSystem system = system("https://api.example.com/platform/", "/actuator/health", 1000);

        HealthProbeResult result = client.probe(system);

        ArgumentCaptor<ClientRequest> requestCaptor = ArgumentCaptor.forClass(ClientRequest.class);
        verify(exchangeFunction).exchange(requestCaptor.capture());
        assertThat(requestCaptor.getValue().method().name()).isEqualTo("GET");
        assertThat(requestCaptor.getValue().url())
                .isEqualTo(URI.create("https://api.example.com/platform/actuator/health"));
        assertThat(result.httpStatus()).isEqualTo(503);
        assertThat(result.failureType()).isEqualTo(ProbeFailureType.NONE);
        assertThat(result.reachedServer()).isTrue();
        assertThat(result.responseTimeMs()).isNotNegative();
    }

    @Test
    void shouldResolveEndpointWithoutLeadingSlashAgainstBaseWithoutTrailingSlash() {
        MonitoredSystem system = system("https://api.example.com/v1", "health", 1000);

        URI result = client.resolveHealthUri(system);

        assertThat(result).isEqualTo(URI.create("https://api.example.com/v1/health"));
    }

    @Test
    void shouldReturnUnexpectedFailureWhenExchangeCompletesWithoutHttpResponse() {
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.empty());

        HealthProbeResult result = client.probe(system("https://api.example.com", "/health", 1000));

        assertThat(result.failureType()).isEqualTo(ProbeFailureType.UNEXPECTED);
        assertThat(result.errorMessage()).isEqualTo("Resposta HTTP vazia");
    }

    @Test
    void shouldClassifyDnsFailureAndPreserveUsefulMessage() {
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(Mono.error(new UnknownHostException("unknown.api.internal")));

        HealthProbeResult result = client.probe(system("https://unknown.invalid", "/health", 1000));

        assertThat(result.failureType()).isEqualTo(ProbeFailureType.DNS);
        assertThat(result.httpStatus()).isNull();
        assertThat(result.errorMessage()).contains("unknown.api.internal");
    }

    @Test
    void shouldClassifyConnectionRefusedSeparatelyFromOtherNetworkFailures() {
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(Mono.error(new ConnectException("Connection refused")));

        HealthProbeResult result = client.probe(system("https://offline.example.com", "/health", 1000));

        assertThat(result.failureType()).isEqualTo(ProbeFailureType.CONNECTION_REFUSED);
        assertThat(result.errorMessage()).containsIgnoringCase("refused");
    }

    @Test
    void shouldClassifyGenericConnectExceptionAsNetworkFailure() {
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(Mono.error(new ConnectException("Connection reset")));

        HealthProbeResult result = client.probe(system("https://unstable.example.com", "/health", 1000));

        assertThat(result.failureType()).isEqualTo(ProbeFailureType.NETWORK);
        assertThat(result.errorMessage()).containsIgnoringCase("reset");
    }

    @Test
    void shouldTurnReactiveTimeoutIntoNonThrowingTimeoutResult() {
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.never());

        HealthProbeResult result = client.probe(system("https://slow.example.com", "/health", 20));

        assertThat(result.failureType()).isEqualTo(ProbeFailureType.TIMEOUT);
        assertThat(result.httpStatus()).isNull();
        assertThat(result.responseTimeMs()).isGreaterThanOrEqualTo(0);
        assertThat(result.errorMessage()).isNotBlank();
    }

    @Test
    void shouldRecognizeTimedOutMessageWhenExceptionTypeIsNotTimeoutException() {
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(Mono.error(new IllegalStateException("Request timed out waiting for response")));

        HealthProbeResult result = client.probe(system("https://slow.example.com", "/health", 5000));

        assertThat(result.failureType()).isEqualTo(ProbeFailureType.TIMEOUT);
        assertThat(result.errorMessage()).containsIgnoringCase("timed out");
    }

    @Test
    void shouldClassifyWebClientRequestExceptionAsNetworkFailure() {
        WebClientRequestException exception = new WebClientRequestException(
                new IOException("Socket closed"),
                HttpMethod.GET,
                URI.create("https://api.example.com/health"),
                HttpHeaders.EMPTY
        );
        when(exchangeFunction.exchange(any(ClientRequest.class))).thenReturn(Mono.error(exception));

        HealthProbeResult result = client.probe(system("https://api.example.com", "/health", 1000));

        assertThat(result.failureType()).isEqualTo(ProbeFailureType.NETWORK);
        assertThat(result.errorMessage()).containsIgnoringCase("socket closed");
    }

    @Test
    void shouldClassifyUnexpectedExceptionAndUseClassNameWhenMessageIsMissing() {
        when(exchangeFunction.exchange(any(ClientRequest.class)))
                .thenReturn(Mono.error(new IllegalStateException()));

        HealthProbeResult result = client.probe(system("https://api.example.com", "/health", 1000));

        assertThat(result.failureType()).isEqualTo(ProbeFailureType.UNEXPECTED);
        assertThat(result.errorMessage()).isEqualTo("IllegalStateException");
    }

    @Test
    void shouldRevalidateTargetAndNeverCallWebClientWhenSecurityPolicyBlocksIt() {
        MonitoredSystem system = system("http://169.254.169.254", "/latest/meta-data", 1000);
        when(monitoredUrlPolicy.validate(system.getBaseUrl(), system.getHealthEndpoint()))
                .thenThrow(new UnsafeMonitoredUrlException("Destinos de metadata não são permitidos"));

        HealthProbeResult result = client.probe(system);

        assertThat(result.failureType()).isEqualTo(ProbeFailureType.SECURITY_POLICY);
        assertThat(result.errorMessage()).contains("metadata");
        verify(exchangeFunction, never()).exchange(any());
    }

    private MonitoredSystem system(String baseUrl, String endpoint, int timeoutMs) {
        MonitoredSystem system = new MonitoredSystem();
        system.setBaseUrl(baseUrl);
        system.setHealthEndpoint(endpoint);
        system.setTimeoutMs(timeoutMs);
        return system;
    }

    private ValidatedMonitoredUrl validatedUrl(String rawBaseUrl, String rawEndpoint) {
        String baseUrl = rawBaseUrl.endsWith("/")
                ? rawBaseUrl.substring(0, rawBaseUrl.length() - 1)
                : rawBaseUrl;
        String endpoint = rawEndpoint.startsWith("/") ? rawEndpoint : "/" + rawEndpoint;
        URI target = URI.create(baseUrl + "/").resolve(endpoint.substring(1));
        return new ValidatedMonitoredUrl(baseUrl, endpoint, target, java.util.List.of());
    }
}
