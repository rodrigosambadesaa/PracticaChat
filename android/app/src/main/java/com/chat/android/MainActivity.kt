package com.chat.android

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import net.i2p.android.router.util.ConnectivityAndInternetAccess
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets

class MainActivity : AppCompatActivity() {

    private lateinit var etHost: TextInputEditText
    private lateinit var etPort: TextInputEditText
    private lateinit var etNick: TextInputEditText
    private lateinit var tvNetworkStatus: TextView
    private lateinit var btnCheckInternet: Button
    private lateinit var btnConnect: Button

    private var networkObserver: ConnectivityAndInternetAccess.NetworkObserver? = null
    private var internetRequest: ConnectivityAndInternetAccess.Request? = null
    private val connectivity by lazy {
        ConnectivityAndInternetAccess.Builder().build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etHost = findViewById(R.id.etHost)
        etPort = findViewById(R.id.etPort)
        etNick = findViewById(R.id.etNick)
        tvNetworkStatus = findViewById(R.id.tvNetworkStatus)
        btnCheckInternet = findViewById(R.id.btnCheckInternet)
        btnConnect = findViewById(R.id.btnConnect)

        btnConnect.setOnClickListener { attemptConnection() }
        btnCheckInternet.setOnClickListener { runActiveDiagnostic() }
    }

    override fun onStart() {
        super.onStart()
        // Passive network observation using Gist Android Connectivity library
        networkObserver = ConnectivityAndInternetAccess.observeNetwork(this) { state ->
            runOnUiThread {
                val usable = NetworkOperationPolicy.canStartRemoteRequest(
                    state.connected,
                    ConnectivityAndInternetAccess.hasPhysicalNetwork(this)
                )
                tvNetworkStatus.text = if (usable) "Estado Red: Disponible" else "Estado Red: Sin Conexión"
                tvNetworkStatus.setTextColor(if (usable) Color.parseColor("#2E7D32") else Color.RED)
            }
        }
    }

    private fun runActiveDiagnostic() {
        internetRequest?.cancel()
        if (!NetworkOperationPolicy.canStartRemoteRequest(
                ConnectivityAndInternetAccess.isConnected(this),
                ConnectivityAndInternetAccess.hasPhysicalNetwork(this)
            )
        ) {
            tvNetworkStatus.text = "Estado Red: Sin Conexión"
            tvNetworkStatus.setTextColor(Color.RED)
            btnCheckInternet.isEnabled = true
            Toast.makeText(this, "Sin conexión de red", Toast.LENGTH_SHORT).show()
            return
        }
        tvNetworkStatus.text = "Diagnóstico en curso..."
        btnCheckInternet.isEnabled = false

        internetRequest = connectivity.checkInternetAsync(this) { result ->
            runOnUiThread {
                internetRequest = null
                btnCheckInternet.isEnabled = true
                if (result.reachable) {
                    tvNetworkStatus.text = "Internet OK (${result.reachedHost})"
                    tvNetworkStatus.setTextColor(Color.parseColor("#2E7D32"))
                } else {
                    tvNetworkStatus.text = "Sin acceso a Internet"
                    tvNetworkStatus.setTextColor(Color.RED)
                }
            }
        }
    }

    private fun attemptConnection() {
        val host = etHost.text?.toString()?.trim() ?: ""
        val portStr = etPort.text?.toString()?.trim() ?: ""
        val nick = etNick.text?.toString()?.trim() ?: ""

        if (host.isEmpty() || portStr.isEmpty() || nick.isEmpty()) {
            Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
            return
        }

        // Cheap local gate. The real socket operation still owns its timeouts
        // and exception handling because this state can change immediately.
        val canStartRemoteRequest = NetworkOperationPolicy.canStartRemoteRequest(
            ConnectivityAndInternetAccess.isConnected(this),
            ConnectivityAndInternetAccess.hasPhysicalNetwork(this)
        )
        if (!canStartRemoteRequest) {
            tvNetworkStatus.text = "Estado Red: Sin Conexión"
            tvNetworkStatus.setTextColor(Color.RED)
            Toast.makeText(this, "Sin conexión de red", Toast.LENGTH_SHORT).show()
            return
        }

        val port = portStr.toIntOrNull()
        if (port == null) {
            Toast.makeText(this, "Puerto inválido", Toast.LENGTH_SHORT).show()
            return
        }

        btnConnect.isEnabled = false

        // Run socket connection on background thread
        Thread {
            var socket: Socket? = null
            try {
                val connectedSocket = Socket()
                socket = connectedSocket
                connectedSocket.connect(InetSocketAddress(host, port), SOCKET_CONNECT_TIMEOUT_MS)
                connectedSocket.soTimeout = SOCKET_READ_TIMEOUT_MS
                val reader = BufferedReader(InputStreamReader(connectedSocket.getInputStream(), StandardCharsets.UTF_8))
                val writer = PrintWriter(connectedSocket.getOutputStream(), true)

                writer.println("NICK $nick")
                val response = reader.readLine()

                runOnUiThread {
                    btnConnect.isEnabled = true
                    if (response != null && response.startsWith("ACCEPT ")) {
                        // Successfully connected, pass socket details to ChatActivity
                        ChatSocketClient.activeSocket = connectedSocket
                        ChatSocketClient.reader = reader
                        ChatSocketClient.writer = writer
                        ChatSocketClient.nick = nick

                        val intent = Intent(this, ChatActivity::class.java).apply {
                            putExtra("NICK", nick)
                        }
                        startActivity(intent)
                    } else {
                        connectedSocket.close()
                        // Requirement: Show error if nick exists
                        AlertDialog.Builder(this)
                            .setTitle("Error de Conexión")
                            .setMessage("ERROR: Nick Existente")
                            .setPositiveButton("Aceptar", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                socket?.close()
                if (NetworkOperationPolicy.isNetworkFailure(e)) {
                    runOnUiThread { runActiveDiagnostic() }
                }
                runOnUiThread {
                    btnConnect.isEnabled = true
                    AlertDialog.Builder(this)
                        .setTitle("Error de Red")
                        .setMessage("No se pudo conectar al servidor: ${e.message}")
                        .setPositiveButton("Aceptar", null)
                        .show()
                }
            }
        }.start()
    }

    override fun onStop() {
        networkObserver?.close()
        networkObserver = null
        internetRequest?.cancel()
        internetRequest = null
        super.onStop()
    }

    companion object {
        private const val SOCKET_CONNECT_TIMEOUT_MS = 5_000
        private const val SOCKET_READ_TIMEOUT_MS = 7_000
    }
}
