package com.chat.android

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkOperationPolicyTest {
    @Test
    fun requiresBothUsableConnectivityAndPhysicalTransport() {
        assertTrue(NetworkOperationPolicy.canStartRemoteRequest(true, true))
        assertFalse(NetworkOperationPolicy.canStartRemoteRequest(true, false))
        assertFalse(NetworkOperationPolicy.canStartRemoteRequest(false, true))
        assertFalse(NetworkOperationPolicy.canStartRemoteRequest(false, false))
    }

    @Test
    fun classifiesNetworkFailuresForPostFailureDiagnostics() {
        assertTrue(NetworkOperationPolicy.isNetworkFailure(UnknownHostException()))
        assertTrue(NetworkOperationPolicy.isNetworkFailure(ConnectException()))
        assertTrue(NetworkOperationPolicy.isNetworkFailure(SocketTimeoutException()))
        assertTrue(NetworkOperationPolicy.isNetworkFailure(RuntimeException(IOException())))
    }

    @Test
    fun doesNotDiagnoseApplicationFailuresAsNetworkFailures() {
        assertFalse(NetworkOperationPolicy.isNetworkFailure(IllegalStateException("HTTP 500")))
    }
}
