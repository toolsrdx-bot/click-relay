package com.clickrelay

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.clickrelay.ui.*

private val AppTheme = darkColorScheme(
    background       = Color(0xFF0D1117),
    surface          = Color(0xFF161B22),
    surfaceVariant   = Color(0xFF1C2333),
    surfaceContainer = Color(0xFF1C2333),
    onSurface        = Color(0xFFE6EDF3),
    onSurfaceVariant = Color(0xFFE6EDF3),
    primary          = Color(0xFF4FC3F7),
    onPrimary        = Color(0xFF0D1117),
    error            = Color(0xFFFF7B72),
    onError          = Color(0xFF0D1117),
)

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AppTheme) {
                val context = LocalContext.current
                val uiState by viewModel.uiState.collectAsState()

                // Start foreground service when room is active, stop otherwise
                LaunchedEffect(uiState.screen) {
                    if (uiState.screen == Screen.ROOM_ACTIVE) {
                        context.startForegroundService(Intent(context, RelayService::class.java))
                    } else {
                        context.stopService(Intent(context, RelayService::class.java))
                    }
                }

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
