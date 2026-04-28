package com.clickrelay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clickrelay.network.ConnectionState
import com.clickrelay.network.Desktop
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

enum class Screen { LOGIN, OPEN_ROOM, ROOM_ACTIVE }

data class MainUiState(
    val screen: Screen = Screen.LOGIN,
    // Login
    val username: String = "",
    val password: String = "",
    val loginError: String = "",
    val isLoggingIn: Boolean = false,
    // Open room
    val roomPassword: String = "",
    val roomError: String = "",
    val isOpeningRoom: Boolean = false,
    // Room active
    val desktops: List<Desktop> = emptyList(),
    val lastClickInfo: String = "",
    val connectionState: ConnectionState = ConnectionState.IDLE,
)

class MainViewModel : ViewModel() {

    private val relay = RelayWebSocket(WS_URL)
    private val http = OkHttpClient()
    private var authToken: String = ""

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            relay.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
                if (state == ConnectionState.DISCONNECTED) {
                    _uiState.update { MainUiState() }
                }
            }
        }
        viewModelScope.launch {
            relay.events.collect { event ->
                when (event) {
                    is RelayEvent.AuthOk -> _uiState.update { it.copy(screen = Screen.OPEN_ROOM) }
                    is RelayEvent.RoomOpened -> _uiState.update { it.copy(screen = Screen.ROOM_ACTIVE, isOpeningRoom = false) }
                    is RelayEvent.DesktopList -> _uiState.update { it.copy(desktops = event.desktops) }
                    is RelayEvent.Error -> {
                        if (_uiState.value.screen == Screen.OPEN_ROOM) {
                            _uiState.update { it.copy(roomError = event.message, isOpeningRoom = false) }
                        }
                    }
                    is RelayEvent.Disconnected -> _uiState.update { MainUiState() }
                }
            }
        }
    }

    fun onUsernameChange(v: String) = _uiState.update { it.copy(username = v.trim()) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(password = v) }
    fun onRoomPasswordChange(v: String) = _uiState.update { it.copy(roomPassword = v) }

    fun login() {
        val s = _uiState.value
        if (s.username.isBlank() || s.password.isBlank()) {
            _uiState.update { it.copy(loginError = "Username and password required.") }
            return
        }
        _uiState.update { it.copy(isLoggingIn = true, loginError = "") }
        viewModelScope.launch {
            val result = httpLogin(s.username, s.password)
            result.fold(
                onSuccess = { (token, accountType) ->
                    if (accountType != "controller") {
                        _uiState.update { it.copy(isLoggingIn = false, loginError = "Not a controller account.") }
                        return@launch
                    }
                    authToken = token
                    _uiState.update { it.copy(isLoggingIn = false, username = s.username) }
                    relay.connect(token)
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoggingIn = false, loginError = err.message ?: "Login failed.") }
                }
            )
        }
    }

    fun openRoom() {
        val pw = _uiState.value.roomPassword
        if (pw.isBlank()) {
            _uiState.update { it.copy(roomError = "Room password required.") }
            return
        }
        _uiState.update { it.copy(isOpeningRoom = true, roomError = "") }
        relay.openRoom(pw)
    }

    fun sendClick(cursor: String) {
        if (_uiState.value.screen != Screen.ROOM_ACTIVE) return
        relay.sendClick(cursor)
        _uiState.update { it.copy(lastClickInfo = "Sent click: Cursor $cursor") }
    }

    fun disconnect() {
        relay.disconnect()
        _uiState.update { MainUiState() }
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
