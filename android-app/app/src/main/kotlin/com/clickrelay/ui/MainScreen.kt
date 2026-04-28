package com.clickrelay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clickrelay.MainUiState
import com.clickrelay.network.Desktop

private val BG    = Color(0xFF1E1E2E)
private val SURF  = Color(0xFF313244)
private val GREEN = Color(0xFFA6E3A1)
private val RED   = Color(0xFFF38BA8)
private val BLUE  = Color(0xFF89B4FA)
private val TEXT  = Color(0xFFCDD6F4)
private val SUB   = Color(0xFF6C7086)

@Composable
fun MainScreen(
    uiState: MainUiState,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        Text("Gorilla Controller", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TEXT)
        Spacer(Modifier.height(4.dp))
        Text("● Room open — Volume buttons active", color = GREEN, fontSize = 13.sp)

        Spacer(Modifier.height(24.dp))

        // Volume button guide
        Surface(color = Color(0xFF1E3A2F), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Press Volume Buttons to Click", color = GREEN, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    CursorChip("VOL UP", "Cursor A", BLUE)
                    CursorChip("VOL DOWN", "Cursor B", RED)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Desktop list
        Surface(color = SURF, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Connected Desktops (${uiState.desktops.size})",
                    color = TEXT, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                )
                Spacer(Modifier.height(8.dp))
                if (uiState.desktops.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("Waiting for desktops to join...", color = SUB, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.desktops) { desktop ->
                            DesktopItem(desktop)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (uiState.lastClickInfo.isNotEmpty()) {
            Text(uiState.lastClickInfo, color = GREEN, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = onDisconnect,
            colors = ButtonDefaults.buttonColors(containerColor = RED, contentColor = BG),
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Close Room & Disconnect", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DesktopItem(desktop: Desktop) {
    Surface(color = Color(0xFF45475A), shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("●", color = GREEN, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text(desktop.deviceName, color = TEXT, fontSize = 14.sp, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CursorChip(key: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(color = color.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
            Text(key, color = color, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, color = SUB, fontSize = 12.sp)
    }
}
