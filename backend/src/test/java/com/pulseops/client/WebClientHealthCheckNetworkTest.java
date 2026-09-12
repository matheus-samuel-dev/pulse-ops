package com.pulseops.client;

import com.pulseops.domain.system.MonitoredSystem;
import com.pulseops.dto.monitoring.ProbeFailureType;
import com.pulseops.security.outbound.MonitoredUrlPolicy;
import com.pulseops.security.outbound.OutboundUrlSecurityProperties;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import static org.assertj.core.api.Assertions.*;

class WebClientHealthCheckNetworkTest {
    HttpServer server;
    MonitoredSystem system;

    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/health", exchange -> { exchange.sendResponseHeaders(200, -1); exchange.close(); });
        server.createContext("/offline", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.createContext("/redirect", exchange -> { exchange.getResponseHeaders().add("Location", "http://169.254.169.254/latest/meta-data"); exchange.sendResponseHeaders(302, -1); exchange.close(); });
        server.start();
        system = new MonitoredSystem(); system.setBaseUrl("http://auditor.test:" + server.getAddress().getPort());
        system.setHealthEndpoint("/health"); system.setTimeoutMs(5000);
    }

    @AfterEach void close() { server.stop(0); }

    @Test void pinsValidatedAddressAndDoesNotPerformASecondDnsLookup() {
        AtomicInteger resolutions = new AtomicInteger();
        var policy = new MonitoredUrlPolicy(new OutboundUrlSecurityProperties(true), host -> {
            int call = resolutions.incrementAndGet();
            return new InetAddress[] { InetAddress.getByName(call == 1 ? "127.0.0.1" : "169.254.169.254") };
        });
        var result = new WebClientHealthCheckClient(WebClient.builder(), policy).probe(system);
        assertThat(result.httpStatus()).isEqualTo(200);
        assertThat(resolutions).hasValue(1);
    }

    @Test void preservesHttpFailuresAndDoesNotFollowRedirects() {
        var client = client();
        system.setHealthEndpoint("/offline"); assertThat(client.probe(system).httpStatus()).isEqualTo(503);
        system.setHealthEndpoint("/redirect"); assertThat(client.probe(system).httpStatus()).isEqualTo(302);
    }

    @Test void includesDnsResolutionInTheOverallTimeout() {
        var policy = new MonitoredUrlPolicy(new OutboundUrlSecurityProperties(true), host -> {
            try { Thread.sleep(500); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return new InetAddress[] { InetAddress.getByName("127.0.0.1") };
        });
        system.setTimeoutMs(40);
        assertThat(new WebClientHealthCheckClient(WebClient.builder(), policy).probe(system).failureType()).isEqualTo(ProbeFailureType.TIMEOUT);
    }

    @Test void classifiesDnsFailureBeforeConnecting() {
        var policy = new MonitoredUrlPolicy(new OutboundUrlSecurityProperties(false), host -> { throw new UnknownHostException("DNS unavailable"); });
        assertThat(new WebClientHealthCheckClient(WebClient.builder(), policy).probe(system).failureType()).isEqualTo(ProbeFailureType.DNS);
    }

    @Test void connectionRefusedIsNormalized() {
        server.stop(0);
        assertThat(client().probe(system).failureType()).isEqualTo(ProbeFailureType.CONNECTION_REFUSED);
    }

    private WebClientHealthCheckClient client() {
        return new WebClientHealthCheckClient(WebClient.builder(), new MonitoredUrlPolicy(new OutboundUrlSecurityProperties(true),
                host -> new InetAddress[] { InetAddress.getByName("127.0.0.1") }));
    }
}
