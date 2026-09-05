package com.chat.android

import java.io.IOException

/** Shared policy for deciding whether a failed real operation merits a diagnostic. */
object NetworkOperationPolicy {
    fun isNetworkFailure(error: Throwable): Boolean =
        generateSequence(error) { it.cause }.any { it is IOException }
}
