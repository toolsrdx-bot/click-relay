package com.clickrelay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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

@Composable
fun LoginScreen(
    uiState: MainUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onControllerUsernameChange: (String) -> Unit,
    onRoomPasswordChange: (String) -> Unit,
    onDeviceNameChange: (String) -> Unit,
    onLogin: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BG)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        Text("Gorilla Controller", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = TEXT)
        Spacer(Modifier.height(4.dp))
        Text("Sign in to join a room", fontSize = 13.sp, color = SUB)

        Spacer(Modifier.height(28.dp))

        LoginField("Your Username", uiState.username, onUsernameChange)
        Spacer(Modifier.height(12.dp))
        LoginField("Your Password", uiState.password, onPasswordChange, password = true)
        Spacer(Modifier.height(20.dp))

        Surface(color = SURF, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Room Details", color = SUB, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                LoginField("Controller Username", uiState.controllerUsername, onControllerUsernameChange)
                Spacer(Modifier.height(12.dp))
                LoginField("Room Password", uiState.roomPassword, onRoomPasswordChange, password = true)
                Spacer(Modifier.height(12.dp))
                LoginField("Device Name  (e.g. Office PC)", uiState.deviceName, onDeviceNameChange)
            }
        }

        Spacer(Modifier.height(20.dp))

        if (uiState.loginError.isNotEmpty()) {
            Text(uiState.loginError, color = RED, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = onLogin,
            enabled = !uiState.isLoggingIn,
            colors = ButtonDefaults.buttonColors(containerColor = BLUE, contentColor = BG),
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(
                if (uiState.isLoggingIn) "Signing in..." else "Sign In",
                fontWeight = FontWeight.Bold, fontSize = 16.sp,
            )
        }
    }
}

@Composable
private fun LoginField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = SUB, fontSize = 12.sp) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = if (password) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TEXT,
            unfocusedTextColor = TEXT,
            focusedBorderColor = BLUE,
            unfocusedBorderColor = SUB,
            cursorColor = BLUE,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
