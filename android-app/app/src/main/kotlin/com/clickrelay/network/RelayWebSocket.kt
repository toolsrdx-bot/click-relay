package com.clickrelay.network

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed interface RelayEvent {
    data object AuthOk : RelayEvent
    data object RoomJoined : RelayEvent
    data object ControllerDisconnected : RelayEvent
    data class Error(val message: String) : RelayEvent
    data object Disconnected : RelayEvent
}

enum class ConnectionState { IDLE, CONNECTING, CONNECTED, DISCONNECTED }

class RelayWebSocket(private val serverUrl: String) {

    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    fun connect(token: String, controllerUsername: String, roomPassword: String, deviceName: String) {
        _connectionState.value = ConnectionState.CONNECTING
        val request = Request.Builder().url(serverUrl).build()
        ws = client.newWebSocket(request, AuthListener(token, controllerUsername, roomPassword, deviceName))
    }

    private inner class AuthListener(
        private val token: String,
        private val controllerUsername: String,
        private val roomPassword: String,
        private val deviceName: String,
    ) : WebSocketListener() {

        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(JSONObject().apply {
                put("type", "auth")
                put("token", token)
            }.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val msg = runCatching { JSONObject(text) }.getOrNull() ?: return
            when (msg.optString("type")) {
                "auth_ok" -> {
                    webSocket.send(JSONObject().apply {
                        put("type", "join_room")
                        put("controllerUsername", controllerUsername)
                        put("roomPassword", roomPassword)
                        put("deviceName", deviceName)
                    }.toString())
                    _events.tryEmit(RelayEvent.AuthOk)
                }
                "room_joined" -> {
                    _connectionState.value = ConnectionState.CONNECTED
                    _events.tryEmit(RelayEvent.RoomJoined)
                }
                "controller_disconnected" -> _events.tryEmit(RelayEvent.ControllerDisconnected)
                "error" -> _events.tryEmit(RelayEvent.Error(msg.optString("message")))
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            _connectionState.value = ConnectionState.DISCONNECTED
            _events.tryEmit(RelayEvent.Error(t.message ?: "Connection failed"))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            _connectionState.value = ConnectionState.DISCONNECTED
            _events.tryEmit(RelayEvent.Disconnected)
        }
    }

    fun sendClick(cursor: String) {
        ws?.send(JSONObject().apply {
            put("type", "click")
            put("cursor", cursor)
        }.toString())
    }

    fun disconnect() {
        ws?.close(1000, "User disconnected")
        ws = null
        _connectionState.value = ConnectionState.IDLE
    }
}
