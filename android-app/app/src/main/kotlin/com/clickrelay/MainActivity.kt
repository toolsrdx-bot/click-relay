package com.clickrelay

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    private val vibrator by lazy { getSystemService(VIBRATOR_SERVICE) as Vibrator }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = AppTheme) {
                val context = LocalContext.current
                val uiState by viewModel.uiState.collectAsState()

                LaunchedEffect(uiState.screen) {
                    if (uiState.screen == Screen.ROOM_ACTIVE) {
                        context.startForegroundService(Intent(context, RelayService::class.java))
                    } else {
                        context.stopService(Intent(context, RelayService::class.java))
                    }
                }

                Column(modifier = Modifier.fillMaxSize()) {
                    // Update banner
                    if (uiState.updateAvailable.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF1C3B2A))
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("↑ ${uiState.updateAvailable}", color = Color(0xFF56D364),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        }
                    }

                    when (uiState.screen) {
                        Screen.ONBOARDING -> OnboardingScreen(onDone = viewModel::onboardingDone)
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
                            onToggleDesktop = viewModel::toggleDesktop,
                        )
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)
        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP   -> { vibrate(); viewModel.sendClick("A"); true }
            KeyEvent.KEYCODE_VOLUME_DOWN -> { vibrate(); viewModel.sendClick("B"); true }
            else -> super.dispatchKeyEvent(event)
        }
    }

    private fun vibrate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(40)
        }
    }
}
