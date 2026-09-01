# Práctica Chat Multiplataforma Dockerizada

Este proyecto implementa una solución completa de chat cliente-servidor multiplataforma basada en la especificación técnica de la práctica (`Practica Chat.pdf`, `EsquemaConexion.pdf`, `EsquemaGeneral.pdf`). 

Incluye:
- **Servidor Híbrido Dockerizado (`ServidorChat`)**: Soporta clientes **TCP Sockets** y **WebSockets** compartiendo en tiempo real la misma sala de chat y lista de usuarios activos.
- **Cliente Desktop (`ClienteChatGUI`)**: Aplicación Java Swing con simulación de ventana única, cambio dinámico de estado, gestión de nick repetido con `JOptionPane` e integración del monitor de conectividad desde el Gist de escritorio.
- **Cliente Web (`WebChat`)**: Aplicación Web moderna (HTML5/CSS3/JS) con diseño glassmorphic en modo oscuro, feed en tiempo real e indicador de estado de red.
- **App Android (`AndroidChatApp`)**: Proyecto nativo Kotlin/Java con interfaz optimizada, diálogo de nicks activos e integración del monitor de conectividad del Gist Android.

---

## 🛠️ Estructura del Repositorio

```
PracticaChat/
├── server/                 # Servidor de Chat (TCP Port 9000 & WebSocket Port 9080)
│   ├── src/chat/server/   # Servidor híbrido TCP/WS
│   └── Dockerfile          # Imagen Docker para el Servidor
├── desktop/                # Aplicación Desktop Java Swing
│   ├── src/chat/desktop/  # ServidorChatGUI y ClienteChatGUI
│   └── src/net/i2p/desktop/router/util/ # Gist Desktop Connectivity Monitor
├── web/                    # Aplicación Web Chat
│   ├── index.html
│   ├── style.css
│   ├── app.js
│   └── Dockerfile          # Imagen Docker Nginx para la Web
├── android/                # Aplicación Android Kotlin/Java
│   ├── app/src/main/java/net/i2p/android/router/util/ # Gist Android Connectivity
│   └── app/src/main/java/com/chat/android/ # MainActivity, ChatActivity, SocketClient
├── docker-compose.yml      # Orquestación de servicios
├── Practica Chat.pdf       # Especificación original
├── EsquemaConexion.pdf     # Diagramas de secuencia y protocolo
└── EsquemaGeneral.pdf      # Arquitectura de threads e interfaz
```

---

## 🚀 Despliegue con Docker

Para levantar el Servidor de Chat y la Aplicación Web de forma dockerizada:

```bash
docker compose up --build -d
```

- **Servidor TCP**: Escuchando en `localhost:9000`
- **Servidor WebSocket**: Escuchando en `localhost:9080`
- **Cliente Web**: Disponible en `http://localhost:8080`

---

## 🖥️ Ejecución de la Versión Desktop (Java Swing)

### 1. Compilación
```bash
javac -d desktop/bin desktop/src/net/i2p/desktop/router/util/*.java server/src/chat/server/*.java desktop/src/chat/desktop/*.java
```

### 2. Ejecutar Servidor Chat GUI
```bash
java -cp desktop/bin chat.desktop.ServidorChatGUI
```

### 3. Ejecutar Cliente Chat GUI
```bash
java -cp desktop/bin chat.desktop.ClienteChatGUI
```

---

## 📱 Ejecución de la App Android

1. Abre el directorio `android/` en **Android Studio**.
2. Sincroniza Gradle e inicia un emulador o dispositivo físico.
3. Al conectar desde el emulador:
   - **Host para conectarse al host local**: `10.0.2.2`
   - **Puerto**: `9000`
   - **Nick**: Tu apodo deseado.

Para generar un APK instalable, usa la variante `release`; el proyecto la firma
automáticamente con el keystore debug local si no se configura uno de release.
Para distribución, define `ANDROID_KEYSTORE_PATH`, `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS` y `ANDROID_KEY_PASSWORD` antes de ejecutar
`assembleRelease`.

---

## 📡 Integración de Conectividad (Gists)

1. **Escritorio**: Utiliza `net.i2p.desktop.router.util.ConnectivityAndInternetAccess` para observar interfaces de red en segundo plano (0 tráfico) y ejecutar diagnósticos asíncronos activos (`checkInternetAsync`).
2. **Android**: Utiliza `net.i2p.android.router.util.ConnectivityAndInternetAccess` con observador consciente del ciclo de vida de las actividades (`onStart`/`onStop`) y pruebas activas de alcance a Internet.
