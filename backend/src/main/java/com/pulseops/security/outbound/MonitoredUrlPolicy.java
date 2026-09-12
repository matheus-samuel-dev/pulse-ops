package com.pulseops.security.outbound;

import com.pulseops.exception.UnsafeMonitoredUrlException;
import java.net.IDN;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Validates every monitored URL both when it is persisted and immediately before I/O.
 *
 * <p>All DNS answers must be safe. This is important because accepting one public address from
 * a mixed public/private answer would leave the client exposed to DNS rebinding and round-robin
 * resolution attacks.</p>
 */
@Component
public class MonitoredUrlPolicy {

    private static final Pattern DNS_LABEL = Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?");
    private static final Set<String> CLOUD_METADATA_HOSTS = Set.of(
            "metadata.google.internal",
            "metadata.goog",
            "instance-data.ec2.internal",
            "metadata.azure.internal",
            "metadata.oraclecloud.com",
            "metadata.tencentyun.com"
    );

    private final OutboundUrlSecurityProperties properties;
    private final HostAddressResolver addressResolver;

    public MonitoredUrlPolicy(
            OutboundUrlSecurityProperties properties,
            HostAddressResolver addressResolver
    ) {
        this.properties = properties;
        this.addressResolver = addressResolver;
    }

    /**
     * Returns canonical values and validates the final URI that will leave the application.
     */
    public ValidatedMonitoredUrl validate(String rawBaseUrl, String rawHealthEndpoint) {
        URI baseUri = parseBaseUrl(rawBaseUrl);
        String endpoint = normalizeEndpoint(rawHealthEndpoint);
        URI target = resolveSameOrigin(baseUri, endpoint);
        InetAddress[] addresses = validateDestination(target.getHost());
        return new ValidatedMonitoredUrl(canonicalBaseUrl(baseUri), endpoint, target, java.util.List.of(addresses));
    }

    private URI parseBaseUrl(String rawBaseUrl) {
        String value = requiredTrimmed(rawBaseUrl, "A URL base é obrigatória");
        rejectUnsafeCharacters(value, "A URL base contém caracteres inválidos");

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException exception) {
            throw new UnsafeMonitoredUrlException("A URL base é inválida", exception);
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new UnsafeMonitoredUrlException("A URL base deve usar apenas HTTP ou HTTPS");
        }
        if (uri.isOpaque() || uri.getRawAuthority() == null) {
            throw new UnsafeMonitoredUrlException("A URL base deve possuir um host válido");
        }
        if (uri.getRawUserInfo() != null || uri.getRawAuthority().contains("@")) {
            throw new UnsafeMonitoredUrlException("Credenciais embutidas na URL não são permitidas");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new UnsafeMonitoredUrlException(
                    "A URL base não pode conter query string ou fragmento; use o health endpoint"
            );
        }
        if (uri.getPort() == 0 || uri.getPort() > 65_535) {
            throw new UnsafeMonitoredUrlException("A URL base contém uma porta inválida");
        }

        String host = canonicalHost(uri.getHost());
        validateHostSyntax(host);
        return rebuildBaseUri(uri, scheme, host);
    }

    private String normalizeEndpoint(String rawEndpoint) {
        String value = requiredTrimmed(rawEndpoint, "O health endpoint é obrigatório");
        rejectUnsafeCharacters(value, "O health endpoint contém caracteres inválidos");
        if (value.indexOf('\\') >= 0) {
            throw new UnsafeMonitoredUrlException("O health endpoint contém separadores inválidos");
        }

        URI endpoint;
        try {
            endpoint = new URI(value);
        } catch (URISyntaxException exception) {
            throw new UnsafeMonitoredUrlException("O health endpoint é inválido", exception);
        }
        if (endpoint.isAbsolute() || endpoint.getRawAuthority() != null || value.startsWith("//")) {
            throw new UnsafeMonitoredUrlException("O health endpoint deve ser um caminho relativo ao mesmo host");
        }
        if (endpoint.getRawFragment() != null) {
            throw new UnsafeMonitoredUrlException("O health endpoint não pode conter fragmento");
        }

        String path = endpoint.getRawPath() == null ? "" : endpoint.getRawPath();
        String normalizedPath = path.startsWith("/") ? path : "/" + path;
        if (normalizedPath.isEmpty()) {
            normalizedPath = "/";
        }
        return endpoint.getRawQuery() == null
                ? normalizedPath
                : normalizedPath + "?" + endpoint.getRawQuery();
    }

    private URI resolveSameOrigin(URI baseUri, String endpoint) {
        URI resolutionBase = URI.create(canonicalBaseUrl(baseUri) + "/");
        String relativeEndpoint = endpoint.substring(1);
        URI target = resolutionBase.resolve(relativeEndpoint).normalize();

        if (!sameOrigin(baseUri, target)) {
            throw new UnsafeMonitoredUrlException("O health endpoint não pode alterar o host de destino");
        }
        return target;
    }

    private boolean sameOrigin(URI base, URI target) {
        return base.getScheme().equalsIgnoreCase(target.getScheme())
                && canonicalHost(base.getHost()).equals(canonicalHost(target.getHost()))
                && effectivePort(base) == effectivePort(target);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return uri.getScheme().equalsIgnoreCase("https") ? 443 : 80;
    }

    private URI rebuildBaseUri(URI original, String scheme, String host) {
        String hostLiteral = host.indexOf(':') >= 0 ? "[" + host + "]" : host;
        String port = original.getPort() < 0 ? "" : ":" + original.getPort();
        String path = original.getRawPath() == null ? "" : original.getRawPath();
        try {
            return new URI(scheme + "://" + hostLiteral + port + path).normalize();
        } catch (URISyntaxException exception) {
            throw new UnsafeMonitoredUrlException("A URL base é inválida", exception);
        }
    }

    private String canonicalBaseUrl(URI baseUri) {
        String value = baseUri.toASCIIString();
        int minimumLength = baseUri.getScheme().length() + 3;
        while (value.endsWith("/") && value.length() > minimumLength) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private InetAddress[] validateDestination(String rawHost) {
        String host = canonicalHost(rawHost);
        if (isCloudMetadataHost(host)) {
            throw new UnsafeMonitoredUrlException("Destinos de metadata de nuvem não são permitidos");
        }

        InetAddress[] addresses;
        try {
            addresses = addressResolver.resolve(host);
        } catch (UnknownHostException exception) {
            throw new UnsafeMonitoredUrlException("Não foi possível resolver o host monitorado", exception);
        }
        if (addresses == null || addresses.length == 0) {
            throw new UnsafeMonitoredUrlException("O host monitorado não possui endereço resolvível");
        }
        for (InetAddress address : addresses) {
            validateAddress(address);
        }
        return addresses;
    }

    private void validateAddress(InetAddress address) {
        if (address == null || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            throw new UnsafeMonitoredUrlException("O host monitorado resolve para um endereço não roteável");
        }
        if (isCloudMetadataAddress(address)) {
            throw new UnsafeMonitoredUrlException("Destinos de metadata de nuvem não são permitidos");
        }
        if (address.isLinkLocalAddress()) {
            throw new UnsafeMonitoredUrlException("Endereços link-local não são permitidos");
        }
        if (isReservedAddress(address)) {
            throw new UnsafeMonitoredUrlException("O host monitorado resolve para um endereço reservado");
        }
        if (!properties.allowPrivateNetworks() && isPrivateAddress(address)) {
            throw new UnsafeMonitoredUrlException(
                    "Redes privadas e loopback estão bloqueados para monitoramento externo"
            );
        }
    }

    private boolean isPrivateAddress(InetAddress address) {
        return address.isLoopbackAddress()
                || address.isSiteLocalAddress()
                || isIpv6UniqueLocal(address)
                || isCarrierGradeNat(address);
    }

    private boolean isCloudMetadataHost(String host) {
        return CLOUD_METADATA_HOSTS.stream()
                .anyMatch(metadataHost -> host.equals(metadataHost) || host.endsWith("." + metadataHost));
    }

    private boolean isCloudMetadataAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            return matches(bytes, 169, 254, 169, 254)
                    || matches(bytes, 169, 254, 170, 2)
                    || matches(bytes, 169, 254, 0, 23)
                    || matches(bytes, 100, 100, 100, 200);
        }
        return address.getHostAddress().equalsIgnoreCase("fd00:ec2:0:0:0:0:0:254")
                || address.getHostAddress().equalsIgnoreCase("fd20:ce:0:0:0:0:0:254");
    }

    private boolean isReservedAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = unsigned(bytes[0]);
            int second = unsigned(bytes[1]);
            int third = unsigned(bytes[2]);
            return first == 0
                    || first >= 224
                    || (first == 192 && second == 0 && (third == 0 || third == 2))
                    || (first == 198 && (second == 18 || second == 19))
                    || (first == 198 && second == 51 && third == 100)
                    || (first == 203 && second == 0 && third == 113);
        }
        if (address instanceof Inet6Address) {
            return unsigned(bytes[0]) == 0x20
                    && unsigned(bytes[1]) == 0x01
                    && unsigned(bytes[2]) == 0x0d
                    && unsigned(bytes[3]) == 0xb8;
        }
        return true;
    }

    private boolean isCarrierGradeNat(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4
                && unsigned(bytes[0]) == 100
                && (unsigned(bytes[1]) & 0xc0) == 0x40;
    }

    private boolean isIpv6UniqueLocal(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (unsigned(bytes[0]) & 0xfe) == 0xfc;
    }

    private boolean matches(byte[] value, int first, int second, int third, int fourth) {
        return value.length == 4
                && unsigned(value[0]) == first
                && unsigned(value[1]) == second
                && unsigned(value[2]) == third
                && unsigned(value[3]) == fourth;
    }

    private int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private String canonicalHost(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            throw new UnsafeMonitoredUrlException("A URL base deve possuir um host válido");
        }
        String host = rawHost;
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.endsWith(".")) {
            host = host.substring(0, host.length() - 1);
        }
        if (host.indexOf('%') >= 0) {
            throw new UnsafeMonitoredUrlException("Identificadores de interface IPv6 não são permitidos");
        }
        if (host.indexOf(':') >= 0) {
            return host.toLowerCase(Locale.ROOT);
        }
        try {
            return IDN.toASCII(host, IDN.USE_STD3_ASCII_RULES).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new UnsafeMonitoredUrlException("A URL base possui um host inválido", exception);
        }
    }

    private void validateHostSyntax(String host) {
        if (host.length() > 253 || host.isBlank()) {
            throw new UnsafeMonitoredUrlException("A URL base possui um host inválido");
        }
        if (host.indexOf(':') >= 0) {
            return;
        }
        for (String label : host.split("\\.", -1)) {
            if (!DNS_LABEL.matcher(label).matches()) {
                throw new UnsafeMonitoredUrlException("A URL base possui um host inválido");
            }
        }
    }

    private String requiredTrimmed(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new UnsafeMonitoredUrlException(message);
        }
        return value.trim();
    }

    private void rejectUnsafeCharacters(String value, String message) {
        if (value.chars().anyMatch(character -> Character.isWhitespace(character)
                || Character.isISOControl(character))) {
            throw new UnsafeMonitoredUrlException(message);
        }
    }
}
