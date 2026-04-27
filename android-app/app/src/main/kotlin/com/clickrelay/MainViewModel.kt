package com.clickrelay

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.clickrelay.network.ConnectionState
import com.clickrelay.network.RelayEvent
import com.clickrelay.network.RelayWebSocket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val sessionCode: String = "",
    val connectionState: ConnectionState = ConnectionState.IDLE,
    val statusMessage: String = "Enter the session code shown on your desktop app",
    val lastClickInfo: String = "",
    val isActive: Boolean = false,
)

class MainViewModel : ViewModel() {

    private val relay = RelayWebSocket(SERVER_URL)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState = _uiState.asStateFlow()

    val connectionState = relay.connectionState

    init {
        viewModelScope.launch {
            relay.connectionState.collect { state ->
                _uiState.update { it.copy(connectionState = state) }
            }
        }
        viewModelScope.launch {
            relay.events.collect { event ->
                when (event) {
                    is RelayEvent.SessionJoined -> _uiState.update {
                        it.copy(
                            statusMessage = "Joined session. Waiting for desktop...",
                            isActive = false
                        )
                    }
                    is RelayEvent.DesktopConnected -> _uiState.update {
                        it.copy(
                            statusMessage = "Desktop connected! Volume buttons are active.",
                            isActive = true
                        )
                    }
                    is RelayEvent.DesktopDisconnected -> _uiState.update {
                        it.copy(
                            statusMessage = "Desktop disconnected.",
                            isActive = false
                        )
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

    fun onSessionCodeChange(code: String) {
        _uiState.update { it.copy(sessionCode = code.filter { c -> c.isDigit() }.take(6)) }
    }

    fun connect() {
        val code = _uiState.value.sessionCode
        if (code.length != 6) return
        _uiState.update { it.copy(statusMessage = "Connecting...") }
        relay.connect(code)
    }

    fun disconnect() {
        relay.disconnect()
        _uiState.update {
            it.copy(
                statusMessage = "Enter the session code shown on your desktop app",
                isActive = false
            )
        }
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

    companion object {
        const val SERVER_URL = "ws://103.164.3.212:8080"
    }
}
