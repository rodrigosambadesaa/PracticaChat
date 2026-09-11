package com.chat.android

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import net.i2p.android.router.util.ConnectivityAndInternetAccess

class ChatActivity : AppCompatActivity() {

    private lateinit var tvChatTitle: TextView
    private lateinit var btnUsers: ImageButton
    private lateinit var btnDisconnect: ImageButton
    private lateinit var lvMessages: ListView
    private lateinit var etMessage: EditText
    private lateinit var btnSend: Button

    private val messagesList = ArrayList<String>()
    private lateinit var messagesAdapter: ArrayAdapter<String>

    private val onlineNicks = ArrayList<String>()
    private var isListening = true
    private var nickName = ""
    private var networkObserver: ConnectivityAndInternetAccess.NetworkObserver? = null
    private var lastObservedConnected: Boolean? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        nickName = intent.getStringExtra("NICK") ?: ChatSocketClient.nick

        tvChatTitle = findViewById(R.id.tvChatTitle)
        btnUsers = findViewById(R.id.btnUsers)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        lvMessages = findViewById(R.id.lvMessages)
        etMessage = findViewById(R.id.etMessage)
        btnSend = findViewById(R.id.btnSend)

        // Requirement: Title format
        tvChatTitle.text = "Cliente Chat conectado: Nick $nickName"

        messagesAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, messagesList)
        lvMessages.adapter = messagesAdapter

        btnSend.setOnClickListener { sendMessage() }
        btnUsers.setOnClickListener { showUsersDialog() }
        btnDisconnect.setOnClickListener { disconnectAndFinish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                disconnectAndFinish()
            }
        })

        startListeningThread()
    }

    override fun onStart() {
        super.onStart()
        networkObserver = ConnectivityAndInternetAccess.observeNetwork(this) { state ->
            if (state.captivePortalDetected) {
                Toast.makeText(this, R.string.captive_portal, Toast.LENGTH_SHORT).show()
            } else if (lastObservedConnected == true && !state.connected) {
                Toast.makeText(this, R.string.toast_no_network, Toast.LENGTH_SHORT).show()
            } else if (lastObservedConnected == false && state.connected) {
                Toast.makeText(this, R.string.connected_again, Toast.LENGTH_SHORT).show()
            }
            lastObservedConnected = state.connected
        }
    }

    private fun startListeningThread() {
        Thread {
            val reader = ChatSocketClient.reader ?: return@Thread
            try {
                while (isListening) {
                    val msg = reader.readLine()?.trim() ?: break
                    runOnUiThread { processServerMessage(msg) }
                }
            } catch (_: Exception) {
                if (isListening) {
                    runOnUiThread {
                        messagesList.add("--- Conexión perdida con el servidor ---")
                        messagesAdapter.notifyDataSetChanged()
                    }
                }
            }
        }.start()
    }

    private fun processServerMessage(msg: String) {
        if (msg.startsWith("CHAT ")) {
            val chatText = msg.substring(5)
            messagesList.add(chatText)
            messagesAdapter.notifyDataSetChanged()
            lvMessages.smoothScrollToPosition(messagesList.size - 1)

        } else if (msg.startsWith("EVENT ")) {
            val eventText = msg.substring(6)
            messagesList.add("*** $eventText ***")
            messagesAdapter.notifyDataSetChanged()
            lvMessages.smoothScrollToPosition(messagesList.size - 1)

        } else if (msg.startsWith("LIST ")) {
            val nicksStr = msg.substring(5)
            onlineNicks.clear()
            if (nicksStr.isNotEmpty()) {
                nicksStr.split(",").forEach { n ->
                    if (n.trim().isNotEmpty()) {
                        onlineNicks.add(n.trim())
                    }
                }
            }
        }
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isNotEmpty()) {
            if (!NetworkOperationPolicy.hasUsableNetwork(this)) {
                Toast.makeText(this, R.string.toast_no_network, Toast.LENGTH_SHORT).show()
                return
            }
            Thread {
                try {
                    val writer = ChatSocketClient.writer ?: throw java.io.IOException("writer unavailable")
                    writer.println("MSG $text")
                    if (writer.checkError()) throw java.io.IOException("write failed")
                } catch (error: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, R.string.connection_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
            etMessage.setText("")
        }
    }

    private fun showUsersDialog() {
        val nicksArray = onlineNicks.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Usuarios Conectados (${onlineNicks.size})")
            .setItems(if (nicksArray.isNotEmpty()) nicksArray else arrayOf("Ninguno"), null)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun disconnectAndFinish() {
        isListening = false
        Thread {
            ChatSocketClient.disconnect()
        }.start()
        finish()
    }

    override fun onStop() {
        networkObserver?.close()
        networkObserver = null
        lastObservedConnected = null
        super.onStop()
    }

}
