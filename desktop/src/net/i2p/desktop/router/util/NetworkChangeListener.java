/*
 * SPDX-License-Identifier: MIT
 *
 * Java SE Desktop reimplementation of ConnectivityAndInternetAccess.
 */
package net.i2p.desktop.router.util;

/**
 * Listener interface for receiving notifications about local network state changes
 * and optional diagnostic probe results.
 */
public interface NetworkChangeListener {

    /**
     * Called when a material change in local network state is detected and debounced.
     *
     * @param state the new immutable NetworkState snapshot
     */
    void onNetworkStateChanged(NetworkState state);

    /**
     * Called when automatic diagnostics complete after a material network state change.
     * This method is only called if automaticDiagnosticsOnNetworkChanges is enabled.
     *
     * @param state  the NetworkState snapshot corresponding to the diagnostic execution
     * @param result the diagnostic result
     */
    default void onDiagnosticResult(NetworkState state, ConnectivityAndInternetAccess.InternetResult result) {
    }
}
