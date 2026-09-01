package com.pulseops.security.outbound;

import com.pulseops.exception.UnsafeMonitoredUrlException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("MonitoredUrlPolicy")
class MonitoredUrlPolicyTest {

    @Mock
    private HostAddressResolver addressResolver;

    private MonitoredUrlPolicy policy;

    @BeforeEach
    void setUp() {
        policy = policy(false);
    }

    @Test
    void shouldCanonicalizePublicHttpTargetAndKeepItOnTheConfiguredOrigin() throws Exception {
        givenResolution("api.example.com", "8.8.8.8");

        ValidatedMonitoredUrl result = policy.validate(
                "  HTTPS://API.EXAMPLE.COM/platform/  ", "health?details=full");

        assertThat(result.baseUrl()).isEqualTo("https://api.example.com/platform");
        assertThat(result.healthEndpoint()).isEqualTo("/health?details=full");
        assertThat(result.targetUri().toASCIIString())
                .isEqualTo("https://api.example.com/platform/health?details=full");
        verify(addressResolver).resolve("api.example.com");
    }

    @Test
    void shouldAcceptPublicIpv6Literal() throws Exception {
        givenResolution("2606:4700:4700::1111", "2606:4700:4700::1111");

        ValidatedMonitoredUrl result = policy.validate(
                "https://[2606:4700:4700::1111]:8443", "/ready");

        assertThat(result.targetUri().toASCIIString())
                .isEqualTo("https://[2606:4700:4700::1111]:8443/ready");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://api.example.com",
            "file:///etc/passwd",
            "api.example.com",
            "https:///health"
    })
    void shouldAcceptOnlyAbsoluteHttpOrHttpsUrlsWithHost(String url) {
        assertThatThrownBy(() -> policy.validate(url, "/health"))
                .isInstanceOf(UnsafeMonitoredUrlException.class);

        verifyNoInteractions(addressResolver);
    }

    @Test
    void shouldRejectEmbeddedCredentialsWithoutLeakingThemToDns() {
        assertThatThrownBy(() -> policy.validate(
                "https://admin:super-secret@api.example.com", "/health"))
                .isInstanceOf(UnsafeMonitoredUrlException.class)
                .hasMessageContaining("Credenciais embutidas");

        verifyNoInteractions(addressResolver);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://bad_host.example.com",
            "https://example.com:70000",
            "https://example.com/health?from=base",
            "https://example.com/health#status",
            "https://example.com/he alth"
    })
    void shouldRejectMalformedOrAmbiguousBaseUrls(String url) {
        assertThatThrownBy(() -> policy.validate(url, "/health"))
                .isInstanceOf(UnsafeMonitoredUrlException.class);

        verifyNoInteractions(addressResolver);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "//169.254.169.254/latest/meta-data",
            "https://evil.example.com/health",
            "\\\\evil.example.com\\health",
            "/health#fragment"
    })
    void shouldRejectEndpointsThatCanEscapeOrHaveAmbiguousSemantics(String endpoint) {
        assertThatThrownBy(() -> policy.validate("https://api.example.com", endpoint))
                .isInstanceOf(UnsafeMonitoredUrlException.class);

        verifyNoInteractions(addressResolver);
    }

    @ParameterizedTest(name = "blocks private address {0}")
    @MethodSource("privateAddresses")
    void shouldBlockPrivateLoopbackAndUniqueLocalAddressesByDefault(String address) throws Exception {
        givenResolution("internal.example.com", address);

        assertThatThrownBy(() -> policy.validate("https://internal.example.com", "/health"))
                .isInstanceOf(UnsafeMonitoredUrlException.class)
                .hasMessageContaining("bloqueados");
    }

    @Test
    void shouldRejectMixedDnsAnswersWhenAnyAddressIsPrivate() throws Exception {
        when(addressResolver.resolve("rebind.example.com")).thenReturn(new InetAddress[]{
                address("8.8.8.8"), address("127.0.0.1")
        });

        assertThatThrownBy(() -> policy.validate("https://rebind.example.com", "/health"))
                .isInstanceOf(UnsafeMonitoredUrlException.class)
                .hasMessageContaining("privadas");
    }

    @ParameterizedTest
    @ValueSource(strings = {"169.254.1.10", "fe80::1"})
    void shouldAlwaysRejectLinkLocalAddresses(String address) throws Exception {
        MonitoredUrlPolicy developmentPolicy = policy(true);
        givenResolution("link-local.example.com", address);

        assertThatThrownBy(() -> developmentPolicy.validate(
                "http://link-local.example.com", "/health"))
                .isInstanceOf(UnsafeMonitoredUrlException.class)
                .hasMessageContaining("link-local");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "metadata.google.internal",
            "metadata.goog",
            "instance-data.ec2.internal"
    })
    void shouldAlwaysRejectKnownCloudMetadataHostnamesBeforeDns(String host) {
        MonitoredUrlPolicy developmentPolicy = policy(true);

        assertThatThrownBy(() -> developmentPolicy.validate("http://" + host, "/computeMetadata/v1"))
                .isInstanceOf(UnsafeMonitoredUrlException.class)
                .hasMessageContaining("metadata");

        verifyNoInteractions(addressResolver);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "169.254.169.254",
            "169.254.170.2",
            "100.100.100.200",
            "fd00:ec2::254",
            "fd20:ce::254"
    })
    void shouldAlwaysRejectCloudMetadataAddressesEvenInDevelopment(String address) throws Exception {
        MonitoredUrlPolicy developmentPolicy = policy(true);
        givenResolution("metadata-alias.example.com", address);

        assertThatThrownBy(() -> developmentPolicy.validate(
                "http://metadata-alias.example.com", "/latest/meta-data"))
                .isInstanceOf(UnsafeMonitoredUrlException.class)
                .hasMessageContaining("metadata");
    }

    @Test
    void shouldAllowLoopbackAndPrivateNetworksOnlyWhenExplicitlyEnabled() throws Exception {
        MonitoredUrlPolicy developmentPolicy = policy(true);
        when(addressResolver.resolve("localhost")).thenReturn(new InetAddress[]{
                address("127.0.0.1"), address("::1")
        });

        ValidatedMonitoredUrl result = developmentPolicy.validate(
                "http://localhost:8081", "/actuator/health");

        assertThat(result.targetUri().toASCIIString())
                .isEqualTo("http://localhost:8081/actuator/health");
    }

    @Test
    void shouldExposeDnsResolutionAsValidationFailureWithOriginalCause() throws Exception {
        UnknownHostException failure = new UnknownHostException("offline.example.com");
        when(addressResolver.resolve("offline.example.com")).thenThrow(failure);

        assertThatThrownBy(() -> policy.validate("https://offline.example.com", "/health"))
                .isInstanceOf(UnsafeMonitoredUrlException.class)
                .hasMessageContaining("resolver")
                .hasCause(failure);
    }

    @Test
    void shouldRejectEmptyDnsAnswer() throws Exception {
        when(addressResolver.resolve("empty.example.com")).thenReturn(new InetAddress[0]);

        assertThatThrownBy(() -> policy.validate("https://empty.example.com", "/health"))
                .isInstanceOf(UnsafeMonitoredUrlException.class)
                .hasMessageContaining("resolvível");
    }

    private MonitoredUrlPolicy policy(boolean allowPrivateNetworks) {
        return new MonitoredUrlPolicy(
                new OutboundUrlSecurityProperties(allowPrivateNetworks), addressResolver);
    }

    private void givenResolution(String host, String value) throws Exception {
        when(addressResolver.resolve(host)).thenReturn(new InetAddress[]{address(value)});
    }

    private InetAddress address(String value) throws Exception {
        return InetAddress.getByName(value);
    }

    private static Stream<Arguments> privateAddresses() {
        return Stream.of(
                Arguments.of("127.0.0.1"),
                Arguments.of("::1"),
                Arguments.of("10.10.0.5"),
                Arguments.of("172.20.0.5"),
                Arguments.of("192.168.1.5"),
                Arguments.of("100.64.0.1"),
                Arguments.of("fd12:3456:789a::1")
        );
    }
}
