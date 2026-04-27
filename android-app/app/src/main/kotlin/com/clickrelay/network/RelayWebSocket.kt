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
    data object SessionJoined : RelayEvent
    data object DesktopConnected : RelayEvent
    data object DesktopDisconnected : RelayEvent
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

    fun connect(sessionCode: String) {
        _connectionState.value = ConnectionState.CONNECTING
        val request = Request.Builder().url(serverUrl).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                val payload = JSONObject().apply {
                    put("type", "join_session")
                    put("code", sessionCode)
                }
                webSocket.send(payload.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val msg = JSONObject(text)
                when (msg.getString("type")) {
                    "session_joined" -> {
                        _connectionState.value = ConnectionState.CONNECTED
                        _events.tryEmit(RelayEvent.SessionJoined)
                    }
                    "desktop_connected" -> _events.tryEmit(RelayEvent.DesktopConnected)
                    "desktop_disconnected" -> _events.tryEmit(RelayEvent.DesktopDisconnected)
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
        })
    }

    fun sendClick(cursor: String) {
        val payload = JSONObject().apply {
            put("type", "click")
            put("cursor", cursor)
        }
        ws?.send(payload.toString())
    }

    fun disconnect() {
        ws?.close(1000, "User disconnected")
        ws = null
        _connectionState.value = ConnectionState.IDLE
    }
}
