package com.clickrelay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

private val BG   = Color(0xFF0D1117)
private val SURF = Color(0xFF161B22)
private val GREEN = Color(0xFF56D364)
private val RED  = Color(0xFFFF7B72)
private val BLUE = Color(0xFF4FC3F7)
private val TEXT = Color(0xFFE6EDF3)
private val SUB  = Color(0xFF6E7681)

@Composable
fun MainScreen(
    uiState: MainUiState,
    onDisconnect: () -> Unit,
    onToggleDesktop: (id: String, selected: Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(BG).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        Text("Gorilla Controller", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TEXT)
        Spacer(Modifier.height(4.dp))
        Text("● Room open — Volume buttons active", color = GREEN, fontSize = 13.sp)

        Spacer(Modifier.height(24.dp))

        // Volume button guide
        Surface(color = Color(0xFF0D2318), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
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

        // Desktop list with selection + latency
        Surface(color = SURF, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Connected Desktops (${uiState.desktops.size})",
                        color = TEXT, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    Text("Tap to target", color = SUB, fontSize = 11.sp)
                }
                Spacer(Modifier.height(8.dp))
                if (uiState.desktops.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                        Text("Waiting for desktops to join...", color = SUB, fontSize = 13.sp, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.desktops, key = { it.id }) { desktop ->
                            DesktopItem(desktop, onToggle = { onToggleDesktop(desktop.id, !desktop.selected) })
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
private fun DesktopItem(desktop: Desktop, onToggle: () -> Unit) {
    val bgColor = if (desktop.selected) Color(0xFF1C2B3A) else Color(0xFF1C2333)
    val dotColor = if (desktop.selected) GREEN else SUB

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable { onToggle() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("●", color = dotColor, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text(desktop.deviceName, color = TEXT, fontSize = 14.sp, modifier = Modifier.weight(1f))
            // Latency badge
            val latency = desktop.latencyMs
            if (latency != null && latency >= 0) {
                val latColor = when {
                    latency < 60  -> GREEN
                    latency < 150 -> Color(0xFFFFA657)
                    else          -> RED
                }
                Surface(color = latColor.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text("${latency}ms", color = latColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
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
