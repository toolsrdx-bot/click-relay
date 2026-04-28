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
private val BLUE = Color(0xFF89B4FA)
private val RED  = Color(0xFFF38BA8)
private val TEXT = Color(0xFFCDD6F4)
private val SUB  = Color(0xFF6C7086)

@Composable
fun LoginScreen(
    uiState: MainUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLogin: () -> Unit,
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
        Text("Sign in with your controller account", fontSize = 13.sp, color = SUB)
        Spacer(Modifier.height(36.dp))

        OutlinedTextField(
            value = uiState.username,
            onValueChange = onUsernameChange,
            label = { Text("Username", color = SUB) },
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = uiState.password,
            onValueChange = onPasswordChange,
            label = { Text("Password", color = SUB) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(24.dp))

        if (uiState.loginError.isNotEmpty()) {
            Text(uiState.loginError, color = RED, fontSize = 13.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
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
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TEXT,
    unfocusedTextColor = TEXT,
    focusedBorderColor = BLUE,
    unfocusedBorderColor = SUB,
    cursorColor = BLUE,
)
