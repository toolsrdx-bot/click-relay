package com.clickrelay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clickrelay.MainUiState

private val BG    = Color(0xFF1E1E2E)
private val SURF  = Color(0xFF313244)
private val BLUE  = Color(0xFF89B4FA)
private val GREEN = Color(0xFFA6E3A1)
private val RED   = Color(0xFFF38BA8)
private val TEXT  = Color(0xFFCDD6F4)
private val SUB   = Color(0xFF6C7086)

@Composable
fun HomeScreen(
    uiState: MainUiState,
    onManageUsers: () -> Unit,
    onOpenRoom: () -> Unit,
    onLogout: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().background(BG).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        Text("Gorilla Controller", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TEXT)
        Spacer(Modifier.height(4.dp))

        val roleColor = when (uiState.role) {
            "king" -> Color(0xFFCBA6F7)
            "queen" -> Color(0xFFF9E2AF)
            "rook" -> BLUE
            else -> SUB
        }
        Text("● ${uiState.username}  ·  ${uiState.role}", color = roleColor, fontSize = 13.sp)

        Spacer(Modifier.height(48.dp))

        // Manage Users — king and queen only
        if (uiState.role == "king" || uiState.role == "queen") {
            ActionCard(
                title = "Manage Users",
                subtitle = "Create, delete and change roles",
                color = Color(0xFF45475A),
                onClick = onManageUsers,
            )
            Spacer(Modifier.height(16.dp))
        }

        ActionCard(
            title = "Open Room",
            subtitle = "Start a session — desktops join with your room password",
            color = Color(0xFF1E3A5F),
            onClick = onOpenRoom,
        )

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onLogout) {
            Text("Logout", color = RED, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Surface(
        color = color,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, color = TEXT, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = TEXT.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}
