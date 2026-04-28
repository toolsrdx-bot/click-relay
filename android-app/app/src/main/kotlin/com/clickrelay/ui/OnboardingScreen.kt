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

private val BG   = Color(0xFF0D1117)
private val SURF = Color(0xFF161B22)
private val CARD = Color(0xFF1C2333)
private val CYAN = Color(0xFF4FC3F7)
private val TEXT = Color(0xFFE6EDF3)
private val SUB  = Color(0xFF6E7681)
private val GREEN = Color(0xFF56D364)

private data class Step(val icon: String, val title: String, val body: String)

private val STEPS = listOf(
    Step("🦍", "Welcome to Gorilla", "Control any number of desktop PCs from your phone using your volume buttons."),
    Step("🔑", "Sign in as Controller", "Log in with your controller account. The king account manages all users and rooms."),
    Step("🚪", "Open a Room", "Set a room password. Desktop clients join using this password — no login needed on PC."),
    Step("📱", "Volume Buttons = Clicks", "Vol ↑ fires Cursor A · Vol ↓ fires Cursor B\nDrag the floating markers on each desktop to where you want the clicks."),
    Step("🖥️", "Per-Desktop Targeting", "Tap a desktop in the list to toggle whether it receives clicks. Useful for targeting specific machines."),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val step = STEPS[page]

    Column(
        modifier = Modifier.fillMaxSize().background(BG).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(step.icon, fontSize = 64.sp)
        Spacer(Modifier.height(24.dp))
        Text(step.title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TEXT,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Surface(color = CARD, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Text(step.body, fontSize = 15.sp, color = TEXT.copy(alpha = 0.85f),
                lineHeight = 22.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp))
        }

        Spacer(Modifier.height(40.dp))

        // Page dots
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            STEPS.indices.forEach { i ->
                val active = i == page
                Surface(
                    color = if (active) CYAN else SURF,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.size(if (active) 24.dp else 8.dp, 8.dp)
                ) {}
            }
        }

        Spacer(Modifier.height(32.dp))

        if (page < STEPS.lastIndex) {
            Button(
                onClick = { page++ },
                colors = ButtonDefaults.buttonColors(containerColor = CYAN, contentColor = BG),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Next", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDone) { Text("Skip", color = SUB, fontSize = 14.sp) }
        } else {
            Button(
                onClick = onDone,
                colors = ButtonDefaults.buttonColors(containerColor = GREEN, contentColor = BG),
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
    }
}
