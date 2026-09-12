package com.pulseops.security.outbound;

import io.netty.util.concurrent.DefaultEventExecutor;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PinnedAddressResolverGroupTest {
    @Test void resolvesOnlyTheApprovedOriginAndAddresses() throws Exception {
        var executor = new DefaultEventExecutor();
        var target = new ValidatedMonitoredUrl("https://auditor.test", "/health", URI.create("https://auditor.test/health"),
                List.of(InetAddress.getByName("93.184.216.34")));
        try (var group = new PinnedAddressResolverGroup(target)) {
            var resolver = group.getResolver(executor);
            var result = resolver.resolveAll(InetSocketAddress.createUnresolved("auditor.test", 443)).syncUninterruptibly().getNow();
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().getAddress().getHostAddress()).isEqualTo("93.184.216.34");
            assertThat(resolver.resolve(InetSocketAddress.createUnresolved("metadata.google.internal", 443)).awaitUninterruptibly().isSuccess()).isFalse();
            assertThat(resolver.resolve(InetSocketAddress.createUnresolved("auditor.test", 80)).awaitUninterruptibly().isSuccess()).isFalse();
        } finally { executor.shutdownGracefully(0, 1, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly(); }
    }

    @Test void supportsAnApprovedIpv6LiteralWithoutAnotherDnsLookup() throws Exception {
        var executor = new DefaultEventExecutor();
        var target = new ValidatedMonitoredUrl("https://[2606:4700::1111]", "/health", URI.create("https://[2606:4700::1111]/health"),
                List.of(InetAddress.getByName("2606:4700::1111")));
        try (var group = new PinnedAddressResolverGroup(target)) {
            assertThat(group.getResolver(executor).resolve(InetSocketAddress.createUnresolved("2606:4700::1111", 443)).syncUninterruptibly().getNow().isUnresolved()).isFalse();
        } finally { executor.shutdownGracefully(0, 1, java.util.concurrent.TimeUnit.SECONDS).syncUninterruptibly(); }
    }
}
