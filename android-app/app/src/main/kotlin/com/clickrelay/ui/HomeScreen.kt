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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clickrelay.MainUiState

private val BG     = Color(0xFF0D1117)
private val SURF   = Color(0xFF161B22)
private val CARD   = Color(0xFF1C2333)
private val CYAN   = Color(0xFF4FC3F7)
private val GREEN  = Color(0xFF56D364)
private val RED    = Color(0xFFFF7B72)
private val TEXT   = Color(0xFFE6EDF3)
private val SUB    = Color(0xFF6E7681)
private val PURPLE = Color(0xFFD2A8FF)
private val AMBER  = Color(0xFFFFA657)

@Composable
fun HomeScreen(
    uiState: MainUiState,
    onManageUsers: () -> Unit,
    onOpenRoom: () -> Unit,
    onLogout: () -> Unit,
) {
    var showPrivileges by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().background(BG).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        Text("Gorilla Controller", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TEXT)
        Spacer(Modifier.height(4.dp))

        val roleColor = when (uiState.role) {
            "king" -> PURPLE; "queen" -> AMBER; "rook" -> CYAN; else -> SUB
        }
        Text("● ${uiState.username}  ·  ${uiState.role}", color = roleColor, fontSize = 13.sp)

        Spacer(Modifier.height(32.dp))

        if (uiState.role == "king" || uiState.role == "queen") {
            ActionCard("Manage Users", "Create, delete and change roles", CARD, onManageUsers)
            Spacer(Modifier.height(14.dp))
        }
        ActionCard("Open Room", "Start a session — desktops join with your room password",
            Color(0xFF0D2340), onOpenRoom)

        Spacer(Modifier.height(20.dp))

        // Privilege info
        Surface(color = SURF, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(),
            onClick = { showPrivileges = !showPrivileges }) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Role Privileges", color = TEXT, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        modifier = Modifier.weight(1f))
                    Text(if (showPrivileges) "▴" else "▾", color = SUB, fontSize = 12.sp)
                }
                if (showPrivileges) {
                    Spacer(Modifier.height(10.dp))
                    PrivRow("♛  King",   "All privileges · Unlimited desktops · Full user management", PURPLE)
                    PrivRow("♕  Queen",  "Create/delete rooks & pawns · Unlimited desktops", AMBER)
                    PrivRow("♜  Rook",   "Open rooms · Up to 5 desktops", CYAN)
                    PrivRow("♟  Pawn",   "Open rooms · 1 desktop only", SUB)
                }
            }
        }

        Spacer(Modifier.weight(1f))
        TextButton(onClick = onLogout) { Text("Logout", color = RED, fontSize = 13.sp) }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PrivRow(role: String, desc: String, color: Color) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(role, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(72.dp))
        Text(desc, color = SUB, fontSize = 12.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun ActionCard(title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Surface(color = color, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, color = TEXT, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = TEXT.copy(alpha = 0.6f), fontSize = 13.sp)
        }
    }
}
