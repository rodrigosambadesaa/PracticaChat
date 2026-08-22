/*
 * SPDX-License-Identifier: MIT
 *
 * Java SE Desktop reimplementation of ConnectivityAndInternetAccess.
 */
package net.i2p.desktop.router.util;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of an individual local network interface.
 * <p>
 * This class represents strictly local network configuration captured from the OS kernel.
 * Obtaining interface information does NOT generate network traffic or consume Internet data.
 */
public final class InterfaceState {
    private final String name;
    private final String displayName;
    private final int index;
    private final boolean isUp;
    private final boolean isLoopback;
    private final boolean isVirtual;
    private final int mtu;
    private final byte[] hardwareAddress;
    private final List<InetAddress> addresses;
    private final List<Inet4Address> ipv4Addresses;
    private final List<Inet6Address> ipv6Addresses;

    public InterfaceState(
            String name,
            String displayName,
            int index,
            boolean isUp,
            boolean isLoopback,
            boolean isVirtual,
            int mtu,
            byte[] hardwareAddress,
            List<InetAddress> addresses
    ) {
        this.name = name != null ? name : "";
        this.displayName = displayName != null ? displayName : "";
        this.index = index;
        this.isUp = isUp;
        this.isLoopback = isLoopback;
        this.isVirtual = isVirtual;
        this.mtu = mtu;
        this.hardwareAddress = hardwareAddress != null ? hardwareAddress.clone() : null;

        List<InetAddress> addrList = new ArrayList<>();
        List<Inet4Address> v4List = new ArrayList<>();
        List<Inet6Address> v6List = new ArrayList<>();

        if (addresses != null) {
            for (InetAddress addr : addresses) {
                if (addr != null) {
                    addrList.add(addr);
                    if (addr instanceof Inet4Address) {
                        v4List.add((Inet4Address) addr);
                    } else if (addr instanceof Inet6Address) {
                        v6List.add((Inet6Address) addr);
                    }
                }
            }
        }

        this.addresses = Collections.unmodifiableList(addrList);
        this.ipv4Addresses = Collections.unmodifiableList(v4List);
        this.ipv6Addresses = Collections.unmodifiableList(v6List);
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getIndex() {
        return index;
    }

    public boolean isUp() {
        return isUp;
    }

    public boolean isLoopback() {
        return isLoopback;
    }

    public boolean isVirtual() {
        return isVirtual;
    }

    public int getMtu() {
        return mtu;
    }

    public byte[] getHardwareAddress() {
        return hardwareAddress != null ? hardwareAddress.clone() : null;
    }

    public String getHardwareAddressHex() {
        if (hardwareAddress == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hardwareAddress.length; i++) {
            if (i > 0) sb.append(":");
            sb.append(String.format("%02X", hardwareAddress[i]));
        }
        return sb.toString();
    }

    public List<InetAddress> getAddresses() {
        return addresses;
    }

    public List<Inet4Address> getIpv4Addresses() {
        return ipv4Addresses;
    }

    public List<Inet6Address> getIpv6Addresses() {
        return ipv6Addresses;
    }

    public boolean hasAddresses() {
        return !addresses.isEmpty();
    }

    public boolean isUsable() {
        return isUp && !isLoopback && hasAddresses();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InterfaceState that = (InterfaceState) o;
        return index == that.index &&
                isUp == that.isUp &&
                isLoopback == that.isLoopback &&
                isVirtual == that.isVirtual &&
                mtu == that.mtu &&
                Objects.equals(name, that.name) &&
                Objects.equals(displayName, that.displayName) &&
                Arrays.equals(hardwareAddress, that.hardwareAddress) &&
                Objects.equals(addresses, that.addresses);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(name, displayName, index, isUp, isLoopback, isVirtual, mtu, addresses);
        result = 31 * result + Arrays.hashCode(hardwareAddress);
        return result;
    }

    @Override
    public String toString() {
        return "InterfaceState{" +
                "name='" + name + '\'' +
                ", index=" + index +
                ", isUp=" + isUp +
                ", isLoopback=" + isLoopback +
                ", isVirtual=" + isVirtual +
                ", mtu=" + mtu +
                ", mac=" + getHardwareAddressHex() +
                ", v4Count=" + ipv4Addresses.size() +
                ", v6Count=" + ipv6Addresses.size() +
                '}';
    }
}
