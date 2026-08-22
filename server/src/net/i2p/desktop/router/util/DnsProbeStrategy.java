/*
 * SPDX-License-Identifier: MIT
 *
 * Java SE Desktop reimplementation of ConnectivityAndInternetAccess.
 */
package net.i2p.desktop.router.util;

import java.util.List;

/**
 * Strategy interface for custom DNS probing during Stage 1 of Internet diagnostics.
 * When provided, this strategy completely controls the DNS stage.
 */
public interface DnsProbeStrategy {

    /**
     * Executes the DNS probe phase.
     *
     * @param dnsResolvers configured list of DNS resolvers
     * @param deadline     timestamp in milliseconds by which the probe must finish
     * @return label of the successfully reached DNS endpoint, or null if all DNS probes failed
     */
    String executeDnsStage(List<String> dnsResolvers, long deadline);
}
