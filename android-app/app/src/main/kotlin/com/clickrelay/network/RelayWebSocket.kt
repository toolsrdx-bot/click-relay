package com.clickrelay.network

import android.content.Context
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
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

data class Desktop(
    val id: String,
    val deviceName: String,
    val selected: Boolean = true,
    val latencyMs: Int? = null,
)

sealed interface RelayEvent {
    data object AuthOk : RelayEvent
    data object RoomOpened : RelayEvent
    data class DesktopList(val desktops: List<Desktop>) : RelayEvent
    data class ClickAck(val socketId: String, val latencyMs: Int) : RelayEvent
    data class Error(val message: String) : RelayEvent
    data object Disconnected : RelayEvent
}

enum class ConnectionState { IDLE, CONNECTING, CONNECTED, DISCONNECTED }

class RelayWebSocket(private val serverUrl: String, private val context: Context? = null) {

    private val client = buildClient()
    private var ws: WebSocket? = null

    private val _connectionState = MutableStateFlow(ConnectionState.IDLE)
    val connectionState = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<RelayEvent>(extraBufferCapacity = 16)
    val events = _events.asSharedFlow()

    private fun buildClient(): OkHttpClient {
        return try {
            val ctx = context ?: return defaultClient()
            val cf = CertificateFactory.getInstance("X.509")
            val certInput = ctx.resources.openRawResource(
                ctx.resources.getIdentifier("server_cert", "raw", ctx.packageName)
            )
            val cert = cf.generateCertificate(certInput)
            certInput.close()

            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                setCertificateEntry("gorilla", cert)
            }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            val trustManager = tmf.trustManagers[0] as X509TrustManager
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(trustManager), null)

            OkHttpClient.Builder()
                .sslSocketFactory(sslContext.socketFactory, trustManager)
                .hostnameVerifier { _, _ -> true }
                .pingInterval(20, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .build()
        } catch (e: Exception) {
            defaultClient()
        }
    }

    private fun defaultClient() = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

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
                    "click_ack" -> _events.tryEmit(
                        RelayEvent.ClickAck(
                            socketId = msg.optString("socketId"),
                            latencyMs = msg.optInt("latencyMs", -1),
                        )
                    )
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

    fun sendClick(cursor: String, mode: String = "selected") {
        ws?.send(JSONObject().apply {
            put("type", "click")
            put("cursor", cursor)
            put("mode", mode)
        }.toString())
    }

    fun selectDesktop(id: String, selected: Boolean) {
        ws?.send(JSONObject().apply {
            put("type", "select_desktop")
            put("id", id)
            put("selected", selected)
        }.toString())
    }

    fun disconnect() {
        ws?.close(1000, "User disconnected")
        ws = null
        _connectionState.value = ConnectionState.IDLE
    }
}
