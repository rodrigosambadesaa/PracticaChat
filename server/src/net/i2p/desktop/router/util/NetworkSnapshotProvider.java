/*
 * SPDX-License-Identifier: MIT
 *
 * Java SE Desktop reimplementation of ConnectivityAndInternetAccess.
 */
package net.i2p.desktop.router.util;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Abstraction for retrieving a snapshot of system network state.
 * Enables unit testing and deterministic simulation of network changes without modifying host interfaces.
 */
public interface NetworkSnapshotProvider {

    /**
     * Captures and returns the current NetworkState snapshot.
     *
     * @return a new NetworkState snapshot
     * @throws SocketException if an error occurs inspecting network interfaces
     */
    NetworkState getSnapshot() throws SocketException;

    /**
     * Default implementation that queries the local OS kernel via java.net.NetworkInterface.
     * <p>
     * IMPORTANT: Polling java.net.NetworkInterface is strictly local and generates 0 network traffic.
     */
    final class SystemNetworkSnapshotProvider implements NetworkSnapshotProvider {
        @Override
        public NetworkState getSnapshot() throws SocketException {
            long timestamp = System.currentTimeMillis();
            List<InterfaceState> interfaceStates = new ArrayList<>();
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    NetworkInterface iface = interfaces.nextElement();
                    String name = iface.getName();
                    String displayName = iface.getDisplayName();
                    int index = iface.getIndex();

                    boolean isUp = false;
                    try {
                        isUp = iface.isUp();
                    } catch (SocketException ignored) {
                    }

                    boolean isLoopback = false;
                    try {
                        isLoopback = iface.isLoopback();
                    } catch (SocketException ignored) {
                    }

                    boolean isVirtual = iface.isVirtual();

                    int mtu = -1;
                    try {
                        mtu = iface.getMTU();
                    } catch (SocketException ignored) {
                    }

                    byte[] mac = null;
                    try {
                        mac = iface.getHardwareAddress();
                    } catch (SocketException ignored) {
                    }

                    List<InetAddress> addresses = new ArrayList<>();
                    Enumeration<InetAddress> inetAddresses = iface.getInetAddresses();
                    while (inetAddresses.hasMoreElements()) {
                        addresses.add(inetAddresses.nextElement());
                    }

                    interfaceStates.add(new InterfaceState(
                            name,
                            displayName,
                            index,
                            isUp,
                            isLoopback,
                            isVirtual,
                            mtu,
                            mac,
                            addresses
                    ));
                }
            }

            return new NetworkState(timestamp, interfaceStates);
        }
    }
}
