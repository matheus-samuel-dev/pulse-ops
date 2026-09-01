package com.pulseops.security.outbound;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * DNS boundary kept injectable so URL security rules can be tested without using the network.
 */
@FunctionalInterface
public interface HostAddressResolver {

    InetAddress[] resolve(String host) throws UnknownHostException;
}
