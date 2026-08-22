document.addEventListener('DOMContentLoaded', () => {
    // DOM Elements
    const connectionScreen = document.getElementById('connectionScreen');
    const chatScreen = document.getElementById('chatScreen');
    const connectionForm = document.getElementById('connectionForm');
    const txtHost = document.getElementById('txtHost');
    const txtPort = document.getElementById('txtPort');
    const txtNick = document.getElementById('txtNick');

    const chatTitle = document.getElementById('chatTitle');
    const lblMyNick = document.getElementById('lblMyNick');
    const userAvatar = document.getElementById('userAvatar');
    const nickCount = document.getElementById('nickCount');
    const onlineList = document.getElementById('onlineList');
    const messagesContainer = document.getElementById('messagesContainer');
    const messageForm = document.getElementById('messageForm');
    const txtMessage = document.getElementById('txtMessage');
    const btnDisconnect = document.getElementById('btnDisconnect');

    const errorModal = document.getElementById('errorModal');
    const errorModalText = document.getElementById('errorModalText');
    const btnCloseModal = document.getElementById('btnCloseModal');

    let socket = null;
    let myNick = '';

    // Handle Connection
    connectionForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const host = txtHost.value.trim();
        const port = txtPort.value.trim();
        const nick = txtNick.value.trim();

        if (!host || !port || !nick) return;

        connectToChat(host, port, nick);
    });

    function connectToChat(host, port, nick) {
        myNick = nick;
        const wsUrl = `ws://${host}:${port}`;

        try {
            socket = new WebSocket(wsUrl);
        } catch (err) {
            showErrorModal(`Error de conexión WebSocket: ${err.message}`);
            return;
        }

        socket.onopen = () => {
            // Send initial NICK command
            socket.send(`NICK ${nick}`);
        };

        socket.onmessage = (event) => {
            const rawMsg = event.data.trim();
            handleServerMessage(rawMsg);
        };

        socket.onerror = (err) => {
            console.error('WebSocket Error:', err);
        };

        socket.onclose = () => {
            if (!connectionScreen.classList.contains('hidden')) return;
            showErrorModal('Se ha perdido la conexión con el servidor.');
            disconnect();
        };
    }

    function handleServerMessage(msg) {
        if (msg.startsWith('ACCEPT ')) {
            const acceptedNick = msg.substring(7);
            myNick = acceptedNick;

            // Update UI to Chat View
            lblMyNick.textContent = myNick;
            userAvatar.textContent = myNick.charAt(0).toUpperCase();

            // Requirement: Title change
            document.title = `Cliente Chat conectado: Nick ${myNick}`;
            chatTitle.textContent = `Cliente Chat conectado: Nick ${myNick}`;

            connectionScreen.classList.add('hidden');
            chatScreen.classList.remove('hidden');

        } else if (msg.startsWith('ERROR ')) {
            const errText = msg.substring(6);
            showErrorModal(errText);
            disconnect();

        } else if (msg.startsWith('CHAT ')) {
            const chatContent = msg.substring(5);
            appendChatMessage(chatContent);

        } else if (msg.startsWith('EVENT ')) {
            const eventContent = msg.substring(6);
            appendSystemMessage(eventContent);

        } else if (msg.startsWith('LIST ')) {
            const nicksStr = msg.substring(5);
            updateOnlineList(nicksStr);
        }
    }

    // Message Sending
    messageForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const text = txtMessage.value.trim();
        if (text && socket && socket.readyState === WebSocket.OPEN) {
            socket.send(`MSG ${text}`);
            txtMessage.value = '';
        }
    });

    // Disconnect
    btnDisconnect.addEventListener('click', () => {
        if (socket && socket.readyState === WebSocket.OPEN) {
            socket.send('DISCONNECT');
        }
        disconnect();
    });

    function disconnect() {
        if (socket) {
            socket.onclose = null;
            socket.close();
            socket = null;
        }

        // Reset UI to Connection Screen
        document.title = 'Cliente Chat: Conexión';
        chatScreen.classList.add('hidden');
        connectionScreen.classList.remove('hidden');
        messagesContainer.innerHTML = '';
        onlineList.innerHTML = '';
    }

    function updateOnlineList(nicksStr) {
        onlineList.innerHTML = '';
        if (!nicksStr) {
            nickCount.textContent = '0';
            return;
        }

        const nicks = nicksStr.split(',').map(n => n.trim()).filter(Boolean);
        nickCount.textContent = nicks.length.toString();

        nicks.forEach(nick => {
            const li = document.createElement('li');
            li.className = 'online-item';
            const avatarChar = nick.charAt(0).toUpperCase();
            li.innerHTML = `
                <div class="avatar">${avatarChar}</div>
                <span class="nick-name">${nick}</span>
            `;
            onlineList.appendChild(li);
        });
    }

    function appendChatMessage(fullMsg) {
        // Expected format: "<Nick> dice <message>"
        const parts = fullMsg.split(' dice ');
        const sender = parts.length > 1 ? parts[0] : 'Chat';
        const body = parts.length > 1 ? parts.slice(1).join(' dice ') : fullMsg;

        const isMe = sender.equalsIgnoreCase ? sender.equalsIgnoreCase(myNick) : (sender.toLowerCase() === myNick.toLowerCase());

        const div = document.createElement('div');
        div.className = `msg-bubble chat ${isMe ? 'me' : ''}`;

        if (!isMe) {
            const senderSpan = document.createElement('div');
            senderSpan.className = 'msg-sender';
            senderSpan.textContent = sender;
            div.appendChild(senderSpan);
        }

        const textSpan = document.createElement('div');
        textSpan.textContent = body;
        div.appendChild(textSpan);

        messagesContainer.appendChild(div);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    function appendSystemMessage(text) {
        const div = document.createElement('div');
        div.className = 'msg-bubble system';
        div.textContent = `*** ${text} ***`;
        messagesContainer.appendChild(div);
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }

    function showErrorModal(message) {
        errorModalText.textContent = message;
        errorModal.classList.remove('hidden');
    }

    btnCloseModal.addEventListener('click', () => {
        errorModal.classList.add('hidden');
    });
});
