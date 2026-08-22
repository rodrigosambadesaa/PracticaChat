/*
 * SPDX-License-Identifier: MIT
 *
 * Java SE Desktop reimplementation of ConnectivityAndInternetAccess.
 */
package net.i2p.desktop.router.util;

/**
 * Handle for managing active local network state observation.
 * Implements AutoCloseable to ensure resources are released upon closing.
 */
public interface NetworkObserver extends AutoCloseable {

    /**
     * Returns the latest confirmed local NetworkState.
     *
     * @return latest local NetworkState snapshot
     */
    NetworkState getLastKnownNetworkState();

    /**
     * Forces an immediate snapshot check and evaluation cycle without waiting for the scheduled poll.
     */
    void checkNow();

    /**
     * Stops observation, cancels scheduled polling, pending debounce tasks, and in-flight diagnostics.
     */
    @Override
    void close();

    /**
     * Checks if this observer has been closed.
     *
     * @return true if closed; false otherwise
     */
    boolean isClosed();
}
