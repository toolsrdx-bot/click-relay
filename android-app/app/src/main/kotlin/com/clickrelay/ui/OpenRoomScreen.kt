package com.clickrelay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clickrelay.MainUiState

private val BG   = Color(0xFF1E1E2E)
private val SURF = Color(0xFF313244)
private val BLUE = Color(0xFF89B4FA)
private val RED  = Color(0xFFF38BA8)
private val TEXT = Color(0xFFCDD6F4)
private val SUB  = Color(0xFF6C7086)
private val GREEN = Color(0xFFA6E3A1)

@Composable
fun OpenRoomScreen(
    uiState: MainUiState,
    onRoomPasswordChange: (String) -> Unit,
    onOpenRoom: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Gorilla Controller", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TEXT)
        Spacer(Modifier.height(6.dp))
        Text("● Signed in as ${uiState.username}  ·  ${uiState.role}", fontSize = 13.sp, color = GREEN)
        Spacer(Modifier.height(36.dp))

        Surface(color = SURF, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Open a Room", color = TEXT, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(6.dp))
                Text("Desktop clients will use this password to join your room.", color = SUB, fontSize = 13.sp)
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = uiState.roomPassword,
                    onValueChange = onRoomPasswordChange,
                    label = { Text("Room Password", color = SUB) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TEXT, unfocusedTextColor = TEXT,
                        focusedBorderColor = BLUE, unfocusedBorderColor = SUB, cursorColor = BLUE,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))

                if (uiState.roomError.isNotEmpty()) {
                    Text(uiState.roomError, color = RED, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                }

                Button(
                    onClick = onOpenRoom,
                    enabled = !uiState.isOpeningRoom,
                    colors = ButtonDefaults.buttonColors(containerColor = BLUE, contentColor = BG),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        if (uiState.isOpeningRoom) "Opening..." else "Open Room",
                        fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        TextButton(onClick = onDisconnect) {
            Text("Disconnect", color = RED, fontSize = 13.sp)
        }
    }
}
