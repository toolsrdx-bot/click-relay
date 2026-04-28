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
import org.json.JSONArray

enum class Screen { LOGIN, HOME, USERS, OPEN_ROOM, ROOM_ACTIVE }

data class UserItem(
    val username: String,
    val role: String,
    val accountType: String,
    val createdBy: String,
)

data class MainUiState(
    val screen: Screen = Screen.LOGIN,
    // Login
    val username: String = "",
    val role: String = "",
    val password: String = "",
    val loginError: String = "",
    val isLoggingIn: Boolean = false,
    // Users screen
    val users: List<UserItem> = emptyList(),
    val isLoadingUsers: Boolean = false,
    val usersError: String = "",
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
                if (state == ConnectionState.DISCONNECTED && _uiState.value.screen == Screen.ROOM_ACTIVE) {
                    _uiState.update { it.copy(screen = Screen.HOME) }
                }
            }
        }
        viewModelScope.launch {
            relay.events.collect { event ->
                when (event) {
                    is RelayEvent.AuthOk    -> _uiState.update { it.copy(screen = Screen.OPEN_ROOM, isOpeningRoom = false) }
                    is RelayEvent.RoomOpened -> _uiState.update { it.copy(screen = Screen.ROOM_ACTIVE, isOpeningRoom = false) }
                    is RelayEvent.DesktopList -> _uiState.update { it.copy(desktops = event.desktops) }
                    is RelayEvent.Error     -> _uiState.update { it.copy(roomError = event.message, isOpeningRoom = false) }
                    is RelayEvent.Disconnected -> _uiState.update { it.copy(screen = Screen.HOME) }
                }
            }
        }
    }

    // ── Login ─────────────────────────────────────────────────────
    fun onUsernameChange(v: String) = _uiState.update { it.copy(username = v.trim()) }
    fun onPasswordChange(v: String) = _uiState.update { it.copy(password = v) }

    fun login() {
        val s = _uiState.value
        if (s.username.isBlank() || s.password.isBlank()) {
            _uiState.update { it.copy(loginError = "Username and password required.") }
            return
        }
        _uiState.update { it.copy(isLoggingIn = true, loginError = "") }
        viewModelScope.launch {
            runCatching {
                val body = JSONObject().apply { put("username", s.username); put("password", s.password) }
                    .toString().toRequestBody("application/json".toMediaType())
                val req = Request.Builder().url("$HTTP_URL/login").post(body).build()
                val resp = http.newCall(req).execute()
                val json = JSONObject(resp.body!!.string())
                if (!resp.isSuccessful) error(json.optString("error", "Login failed"))
                Triple(json.getString("token"), json.getString("role"), json.getString("accountType"))
            }.fold(
                onSuccess = { (token, role, accountType) ->
                    if (accountType != "controller") {
                        _uiState.update { it.copy(isLoggingIn = false, loginError = "Not a controller account.") }
                        return@launch
                    }
                    authToken = token
                    _uiState.update { it.copy(isLoggingIn = false, username = s.username, role = role, screen = Screen.HOME) }
                },
                onFailure = { err ->
                    _uiState.update { it.copy(isLoggingIn = false, loginError = err.message ?: "Login failed.") }
                }
            )
        }
    }

    // ── User management ───────────────────────────────────────────
    fun goToUsers() {
        _uiState.update { it.copy(screen = Screen.USERS, usersError = "") }
        loadUsers()
    }

    fun loadUsers() {
        _uiState.update { it.copy(isLoadingUsers = true, usersError = "") }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val req = Request.Builder().url("$HTTP_URL/admin/users")
                        .addHeader("Authorization", "Bearer $authToken").build()
                    val resp = http.newCall(req).execute()
                    val arr = JSONArray(resp.body!!.string())
                    (0 until arr.length()).map { i ->
                        val u = arr.getJSONObject(i)
                        UserItem(u.getString("username"), u.getString("role"),
                            u.getString("account_type"), u.optString("created_by","—"))
                    }
                }
            }.fold(
                onSuccess = { users -> _uiState.update { it.copy(users = users, isLoadingUsers = false) } },
                onFailure = { err -> _uiState.update { it.copy(usersError = err.message ?: "Failed", isLoadingUsers = false) } }
            )
        }
    }

    fun createUser(username: String, password: String, role: String, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val body = JSONObject().apply {
                        put("username", username); put("password", password)
                        put("role", role); put("accountType", "controller")
                    }.toString().toRequestBody("application/json".toMediaType())
                    val req = Request.Builder().url("$HTTP_URL/admin/users")
                        .addHeader("Authorization", "Bearer $authToken").post(body).build()
                    val resp = http.newCall(req).execute()
                    val json = JSONObject(resp.body!!.string())
                    if (!resp.isSuccessful) error(json.optString("error", "Failed"))
                }
            }.fold(
                onSuccess = { loadUsers(); onDone(null) },
                onFailure = { err -> onDone(err.message) }
            )
        }
    }

    fun deleteUser(username: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val req = Request.Builder().url("$HTTP_URL/admin/users/$username")
                        .addHeader("Authorization", "Bearer $authToken").delete().build()
                    http.newCall(req).execute()
                }
            }.onSuccess { loadUsers() }
        }
    }

    fun changeRole(username: String, newRole: String) {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val body = JSONObject().apply { put("newRole", newRole) }
                        .toString().toRequestBody("application/json".toMediaType())
                    val req = Request.Builder().url("$HTTP_URL/admin/users/$username/role")
                        .addHeader("Authorization", "Bearer $authToken")
                        .method("PATCH", body).build()
                    http.newCall(req).execute()
                }
            }.onSuccess { loadUsers() }
        }
    }

    // ── Room ──────────────────────────────────────────────────────
    fun onRoomPasswordChange(v: String) = _uiState.update { it.copy(roomPassword = v) }

    fun goToOpenRoom() {
        _uiState.update { it.copy(screen = Screen.OPEN_ROOM, roomError = "") }
        relay.connect(authToken)
    }

    fun openRoom() {
        val pw = _uiState.value.roomPassword
        if (pw.isBlank()) { _uiState.update { it.copy(roomError = "Room password required.") }; return }
        _uiState.update { it.copy(isOpeningRoom = true, roomError = "") }
        relay.openRoom(pw)
    }

    fun sendClick(cursor: String) {
        if (_uiState.value.screen != Screen.ROOM_ACTIVE) return
        relay.sendClick(cursor)
        _uiState.update { it.copy(lastClickInfo = "Sent click: Cursor $cursor") }
    }

    fun goHome() {
        relay.disconnect()
        _uiState.update { it.copy(screen = Screen.HOME, roomPassword = "", desktops = emptyList(), lastClickInfo = "") }
    }

    fun logout() {
        relay.disconnect()
        authToken = ""
        _uiState.update { MainUiState() }
    }

    override fun onCleared() { super.onCleared(); relay.disconnect() }

    companion object {
        const val HTTP_URL = "http://103.164.3.212:8080"
        const val WS_URL   = "ws://103.164.3.212:8080"
    }
}
