package com.clickrelay

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.clickrelay.ui.MainScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val uiState by viewModel.uiState.collectAsState()
            MainScreen(
                uiState = uiState,
                onSessionCodeChange = viewModel::onSessionCodeChange,
                onConnect = viewModel::connect,
                onDisconnect = viewModel::disconnect,
            )
        }
    }

    // Intercept hardware volume buttons — suppress default behavior and send relay events
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return super.dispatchKeyEvent(event)

        return when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                viewModel.sendClick("A")
                true // suppress default volume change
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                viewModel.sendClick("B")
                true // suppress default volume change
            }
            else -> super.dispatchKeyEvent(event)
        }
    }
}
