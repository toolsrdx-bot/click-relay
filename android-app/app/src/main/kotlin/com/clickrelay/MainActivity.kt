package com.clickrelay

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.clickrelay.ui.LoginScreen
import com.clickrelay.ui.MainScreen
import com.clickrelay.ui.OpenRoomScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            when (uiState.screen) {
                Screen.LOGIN -> LoginScreen(
                    uiState = uiState,
                    onUsernameChange = viewModel::onUsernameChange,
                    onPasswordChange = viewModel::onPasswordChange,
                    onLogin = viewModel::login,
                )
                Screen.OPEN_ROOM -> OpenRoomScreen(
                    uiState = uiState,
                    onRoomPasswordChange = viewModel::onRoomPasswordChange,
                    onOpenRoom = viewModel::openRoom,
                    onDisconnect = viewModel::disconnect,
                )
                Screen.ROOM_ACTIVE -> MainScreen(
                    uiState = uiState,
                    onDisconnect = viewModel::disconnect,
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP   -> { viewModel.sendClick("A"); true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { viewModel.sendClick("B"); true }
            else -> super.dispatchKeyEvent(event)
        }
    }
}
