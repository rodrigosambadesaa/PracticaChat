package com.chat.android

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

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

        startListeningThread()
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
            Thread {
                try {
                    ChatSocketClient.writer?.println("MSG $text")
                } catch (_: Exception) {}
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

    override fun onBackPressed() {
        disconnectAndFinish()
    }
}
