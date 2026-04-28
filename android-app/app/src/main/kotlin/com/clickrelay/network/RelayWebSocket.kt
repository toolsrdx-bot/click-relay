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

data class Desktop(val id: String, val deviceName: String, val selected: Boolean)

sealed interface RelayEvent {
    data object AuthOk : RelayEvent
    data object RoomOpened : RelayEvent
    data class DesktopList(val desktops: List<Desktop>) : RelayEvent
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

    fun connect(token: String) {
        _connectionState.value = ConnectionState.CONNECTING
        val request = Request.Builder().url(serverUrl).build()
        ws = client.newWebSocket(request, object : WebSocketListener() {

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
                        _connectionState.value = ConnectionState.CONNECTED
                        _events.tryEmit(RelayEvent.AuthOk)
                    }
                    "room_opened" -> _events.tryEmit(RelayEvent.RoomOpened)
                    "desktop_joined", "desktop_left", "desktop_list" -> {
                        val arr = msg.optJSONArray("desktops") ?: return
                        val list = (0 until arr.length()).map { i ->
                            val d = arr.getJSONObject(i)
                            Desktop(d.getString("id"), d.getString("deviceName"), d.optBoolean("selected", true))
                        }
                        _events.tryEmit(RelayEvent.DesktopList(list))
                    }
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

    fun openRoom(roomPassword: String) {
        ws?.send(JSONObject().apply {
            put("type", "open_room")
            put("roomPassword", roomPassword)
        }.toString())
    }

    fun sendClick(cursor: String, mode: String = "all") {
        ws?.send(JSONObject().apply {
            put("type", "click")
            put("cursor", cursor)
            put("mode", mode)
        }.toString())
    }

    fun disconnect() {
        ws?.close(1000, "User disconnected")
        ws = null
        _connectionState.value = ConnectionState.IDLE
    }
}
