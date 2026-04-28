package com.clickrelay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clickrelay.network.ConnectionState
import com.clickrelay.network.RelayEvent
import com.clickrelay.network.RelayWebSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

enum class Screen { LOGIN, MAIN }

data class MainUiState(
    val screen: Screen = Screen.LOGIN,
    // Login fields
    val username: String = "",
    val password: String = "",
    val controllerUsername: String = "",
    val roomPassword: String = "",
    val deviceName: String = "",
    val loginError: String = "",
    val isLoggingIn: Boolean = false,
    // Main screen
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val statusMessage: String = "Connecting to room...",
    val lastClickInfo: String = "",
    val isActive: Boolean = false,
)

class MainViewModel : ViewModel() {

    private val relay = RelayWebSocket(WS_URL)
    private val http = OkHttpClient()

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            relay.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
        viewModelScope.launch {
            relay.events.collect { event ->
                when (event) {
                    is RelayEvent.AuthOk -> _uiState.update {
                        it.copy(statusMessage = "Authenticated. Joining room...")
                    }
                    is RelayEvent.RoomJoined -> _uiState.update {
                        it.copy(statusMessage = "In room. Volume buttons are active.", isActive = true)
                    }
                    is RelayEvent.ControllerDisconnected -> _uiState.update {
                        it.copy(statusMessage = "Controller disconnected.", isActive = false)
                    }
                    is RelayEvent.Error -> _uiState.update {
                        it.copy(statusMessage = "Error: ${event.message}", isActive = false)
                    }
                    is RelayEvent.Disconnected -> _uiState.update {
                        it.copy(statusMessage = "Disconnected from relay.", isActive = false)
                    }
                }
            }
        }
    }

    fun onUsernameChange(v: String)           = _uiState.update { it.copy(username = v.trim()) }
    fun onPasswordChange(v: String)           = _uiState.update { it.copy(password = v) }
    fun onControllerUsernameChange(v: String) = _uiState.update { it.copy(controllerUsername = v.trim()) }
    fun onRoomPasswordChange(v: String)       = _uiState.update { it.copy(roomPassword = v) }
    fun onDeviceNameChange(v: String)         = _uiState.update { it.copy(deviceName = v.trim()) }

    fun login() {
        val s = _uiState.value
        if (s.username.isBlank() || s.password.isBlank() ||
            s.controllerUsername.isBlank() || s.roomPassword.isBlank() || s.deviceName.isBlank()) {
            _uiState.update { it.copy(loginError = "All fields are required.") }
            return
        }
        _uiState.update { it.copy(isLoggingIn = true, loginError = "") }
        viewModelScope.launch {
            val result = httpLogin(s.username, s.password)
            result.fold(
                onSuccess = { (token, accountType) ->
                    if (accountType != "desktop") {
                        _uiState.update { it.copy(isLoggingIn = false, loginError = "Not a desktop account.") }
                        return@launch
                    }
                    _uiState.update { it.copy(isLoggingIn = false, screen = Screen.MAIN) }
                    relay.connect(token, s.controllerUsername, s.roomPassword, s.deviceName)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoggingIn = false, loginError = err.message ?: "Login failed.") }
                }
            )
        }
    }

    fun disconnect() {
        relay.disconnect()
        _uiState.update { MainUiState() }
    }

    fun sendClick(cursor: String) {
        if (!_uiState.value.isActive) return
        relay.sendClick(cursor)
        _uiState.update { it.copy(lastClickInfo = "Sent click: Cursor $cursor") }
    }

    override fun onCleared() {
        super.onCleared()
        relay.disconnect()
    }

    private suspend fun httpLogin(username: String, password: String): Result<Pair<String, String>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = JSONObject().apply {
                    put("username", username)
                    put("password", password)
                }.toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url("$HTTP_URL/login").post(body).build()
                val resp = http.newCall(req).execute()
                val json = JSONObject(resp.body!!.string())
                if (!resp.isSuccessful) error(json.optString("error", "Login failed"))
                json.getString("token") to json.getString("accountType")
            }
        }

    companion object {
        const val HTTP_URL = "http://103.164.3.212:8080"
        const val WS_URL   = "ws://103.164.3.212:8080"
    }
}
