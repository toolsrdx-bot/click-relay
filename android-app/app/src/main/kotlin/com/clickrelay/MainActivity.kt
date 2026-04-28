package com.clickrelay

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.clickrelay.ui.*

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
                Screen.HOME -> HomeScreen(
                    uiState = uiState,
                    onManageUsers = viewModel::goToUsers,
                    onOpenRoom = viewModel::goToOpenRoom,
                    onLogout = viewModel::logout,
                )
                Screen.USERS -> UsersScreen(
                    uiState = uiState,
                    onBack = { viewModel.goHome() },
                    onCreateUser = viewModel::createUser,
                    onDeleteUser = viewModel::deleteUser,
                    onChangeRole = viewModel::changeRole,
                )
                Screen.OPEN_ROOM -> OpenRoomScreen(
                    uiState = uiState,
                    onRoomPasswordChange = viewModel::onRoomPasswordChange,
                    onOpenRoom = viewModel::openRoom,
                    onDisconnect = viewModel::goHome,
                )
                Screen.ROOM_ACTIVE -> MainScreen(
                    uiState = uiState,
                    onDisconnect = viewModel::goHome,
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
