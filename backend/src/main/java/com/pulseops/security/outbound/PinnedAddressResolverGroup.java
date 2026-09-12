package com.pulseops.security.outbound;

import io.netty.resolver.AbstractAddressResolver;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.List;

/** Connects only to the addresses that passed policy; no second DNS lookup can rebind the host. */
public final class PinnedAddressResolverGroup extends AddressResolverGroup<InetSocketAddress> {
    private final ValidatedMonitoredUrl target;

    public PinnedAddressResolverGroup(ValidatedMonitoredUrl target) { this.target = target; }

    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) {
        return new AbstractAddressResolver<>(executor, InetSocketAddress.class) {
            @Override protected boolean doIsResolved(InetSocketAddress address) { return false; }

            private List<InetSocketAddress> pinned(InetSocketAddress address) throws UnknownHostException {
                int port = target.targetUri().getPort() >= 0 ? target.targetUri().getPort()
                        : target.targetUri().getScheme().equalsIgnoreCase("https") ? 443 : 80;
                String approvedHost = target.targetUri().getHost().replace("[", "").replace("]", "");
                if (!address.getHostString().replace("[", "").replace("]", "").equalsIgnoreCase(approvedHost)
                        || address.getPort() != port || target.addresses().isEmpty()) {
                    throw new UnknownHostException("Destination does not match the approved health target");
                }
                return target.addresses().stream().map(ip -> new InetSocketAddress(ip, port)).toList();
            }

            @Override protected void doResolve(InetSocketAddress address, Promise<InetSocketAddress> promise) throws Exception {
                promise.setSuccess(pinned(address).getFirst());
            }

            @Override protected void doResolveAll(InetSocketAddress address, Promise<List<InetSocketAddress>> promise) throws Exception {
                promise.setSuccess(pinned(address));
            }
        };
    }
}
