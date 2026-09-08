package com.chat.android

import java.io.IOException

/** Shared policy for deciding whether a failed real operation merits a diagnostic. */
object NetworkOperationPolicy {
    fun canStartRemoteRequest(isConnected: Boolean, hasPhysicalNetwork: Boolean): Boolean =
        isConnected && hasPhysicalNetwork

    fun isNetworkFailure(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { it is IOException }
}
