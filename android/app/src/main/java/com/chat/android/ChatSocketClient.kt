package com.chat.android

import java.io.BufferedReader
import java.io.PrintWriter
import java.net.Socket

object ChatSocketClient {
    var activeSocket: Socket? = null
    var reader: BufferedReader? = null
    var writer: PrintWriter? = null
    var nick: String = ""

    fun disconnect() {
        try {
            writer?.println("DISCONNECT")
        } catch (_: Exception) {}

        try {
            activeSocket?.close()
        } catch (_: Exception) {}

        activeSocket = null
        reader = null
        writer = null
        nick = ""
    }
}
