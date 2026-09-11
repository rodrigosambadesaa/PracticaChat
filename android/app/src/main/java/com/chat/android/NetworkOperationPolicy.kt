package com.chat.android

import android.content.Context
import net.i2p.android.router.util.ConnectivityAndInternetAccess
import java.io.IOException

/** Shared policy for deciding whether a failed real operation merits a diagnostic. */
object NetworkOperationPolicy {
    fun hasUsableNetwork(context: Context): Boolean =
        ConnectivityAndInternetAccess.isConnected(context) &&
            ConnectivityAndInternetAccess.hasUnderlyingNetwork(context)

    fun canStartRemoteRequest(isConnected: Boolean, hasPhysicalNetwork: Boolean): Boolean =
        isConnected && hasPhysicalNetwork

    fun isNetworkFailure(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { it is IOException }
}
