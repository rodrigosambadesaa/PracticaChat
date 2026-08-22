/*
 * SPDX-License-Identifier: MIT
 *
 * Java SE Desktop reimplementation of ConnectivityAndInternetAccess.
 */
package net.i2p.desktop.router.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of local network configuration constructed strictly from system interface details.
 * <p>
 * IMPORTANT: Querying or building a NetworkState is strictly local to the system OS kernel.
 * It generates 0 network traffic (0 DNS, 0 HTTP/HTTPS, 0 TCP probes) and consumes 0 Internet data.
 * <p>
 * A NetworkState answers: "What network state does the device observe locally?"
 * It does NOT convert any local data into an assertion that Internet access is working.
 */
public final class NetworkState {
    private final long timestamp;
    private final List<InterfaceState> interfaces;
    private final List<InterfaceState> usableInterfaces;

    public NetworkState(long timestamp, List<InterfaceState> interfaces) {
        this.timestamp = timestamp;
        List<InterfaceState> allList = new ArrayList<>();
        List<InterfaceState> usableList = new ArrayList<>();

        if (interfaces != null) {
            for (InterfaceState iface : interfaces) {
                if (iface != null) {
                    allList.add(iface);
                    if (iface.isUsable()) {
                        usableList.add(iface);
                    }
                }
            }
        }

        this.interfaces = Collections.unmodifiableList(allList);
        this.usableInterfaces = Collections.unmodifiableList(usableList);
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<InterfaceState> getInterfaces() {
        return interfaces;
    }

    public List<InterfaceState> getUsableInterfaces() {
        return usableInterfaces;
    }

    public boolean hasUsableInterface() {
        return !usableInterfaces.isEmpty();
    }

    public boolean hasIPv4() {
        for (InterfaceState iface : usableInterfaces) {
            if (!iface.getIpv4Addresses().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean hasIPv6() {
        for (InterfaceState iface : usableInterfaces) {
            if (!iface.getIpv6Addresses().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the primary interface (the first non-virtual, active, non-loopback usable interface
     * with IP addresses), or the first usable interface, or null if none available.
     */
    public InterfaceState getPrimaryInterface() {
        for (InterfaceState iface : usableInterfaces) {
            if (!iface.isVirtual()) {
                return iface;
            }
        }
        return usableInterfaces.isEmpty() ? null : usableInterfaces.get(0);
    }

    /**
     * Determines whether there is a material network change compared to another NetworkState.
     * <p>
     * Significant material changes include:
     * <ul>
     *   <li>Interface additions or removals</li>
     *   <li>Interface up/down state toggles</li>
     *   <li>Usable interface count changes (loss or restoration of network)</li>
     *   <li>IP address additions, removals, or changes</li>
     *   <li>IPv4 or IPv6 availability changes</li>
     *   <li>Primary interface changes</li>
     *   <li>MTU, Hardware address (MAC), or Index changes on usable interfaces</li>
     * </ul>
     *
     * @param previous previous NetworkState snapshot
     * @return true if a material change is detected; false otherwise
     */
    public boolean isMaterialChange(NetworkState previous) {
        if (previous == null) {
            return true;
        }
        if (this == previous) {
            return false;
        }

        if (this.interfaces.size() != previous.interfaces.size()) {
            return true;
        }
        if (this.usableInterfaces.size() != previous.usableInterfaces.size()) {
            return true;
        }
        if (this.hasIPv4() != previous.hasIPv4()) {
            return true;
        }
        if (this.hasIPv6() != previous.hasIPv6()) {
            return true;
        }

        InterfaceState thisPrimary = this.getPrimaryInterface();
        InterfaceState prevPrimary = previous.getPrimaryInterface();
        if (!Objects.equals(thisPrimary, prevPrimary)) {
            return true;
        }

        for (int i = 0; i < this.interfaces.size(); i++) {
            InterfaceState thisIface = this.interfaces.get(i);
            InterfaceState prevIface = previous.interfaces.get(i);
            if (!thisIface.equals(prevIface)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NetworkState that = (NetworkState) o;
        return Objects.equals(interfaces, that.interfaces);
    }

    @Override
    public int hashCode() {
        return Objects.hash(interfaces);
    }

    @Override
    public String toString() {
        return "NetworkState{" +
                "timestamp=" + timestamp +
                ", totalInterfaces=" + interfaces.size() +
                ", usableInterfaces=" + usableInterfaces.size() +
                ", hasIPv4=" + hasIPv4() +
                ", hasIPv6=" + hasIPv6() +
                ", primary=" + (getPrimaryInterface() != null ? getPrimaryInterface().getName() : "none") +
                '}';
    }
}
