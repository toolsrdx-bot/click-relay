package com.clickrelay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clickrelay.MainUiState
import com.clickrelay.network.ConnectionState

private val BackgroundColor = Color(0xFF1E1E2E)
private val SurfaceColor    = Color(0xFF313244)
private val GreenColor      = Color(0xFFA6E3A1)
private val RedColor        = Color(0xFFF38BA8)
private val BlueColor       = Color(0xFF89B4FA)
private val TextColor       = Color(0xFFCDD6F4)
private val SubtextColor    = Color(0xFF6C7086)

@Composable
fun MainScreen(
    uiState: MainUiState,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        Text(
            text = "Gorilla Controller",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = TextColor,
        )

        Spacer(Modifier.height(8.dp))

        StatusIndicator(uiState.connectionState, uiState.isActive)

        Spacer(Modifier.height(32.dp))

        if (uiState.isActive) {
            ActivePanel()
            Spacer(Modifier.height(16.dp))
        }

        Surface(
            color = SurfaceColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = uiState.statusMessage,
                color = TextColor,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }

        if (uiState.lastClickInfo.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(text = uiState.lastClickInfo, color = GreenColor, fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onDisconnect,
            colors = ButtonDefaults.buttonColors(containerColor = RedColor, contentColor = BackgroundColor),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Disconnect", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusIndicator(state: ConnectionState, isActive: Boolean) {
    val (color, label) = when {
        isActive -> GreenColor to "● Active — Volume buttons ready"
        state == ConnectionState.CONNECTED -> BlueColor to "● Connected — joining room..."
        state == ConnectionState.CONNECTING -> Color(0xFFFAB387) to "● Connecting..."
        else -> SubtextColor to "● Not connected"
    }
    Text(text = label, color = color, fontSize = 13.sp)
}

@Composable
private fun ActivePanel() {
    Surface(
        color = Color(0xFF1E3A2F),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Volume Buttons Active", color = GreenColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                CursorChip("VOL UP", "Cursor A", BlueColor)
                CursorChip("VOL DOWN", "Cursor B", RedColor)
            }
        }
    }
}

@Composable
private fun CursorChip(key: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
            Text(
                key, color = color, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = SubtextColor, fontSize = 12.sp)
    }
}
