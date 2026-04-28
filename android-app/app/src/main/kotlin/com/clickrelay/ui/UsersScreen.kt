package com.clickrelay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.clickrelay.MainUiState
import com.clickrelay.UserItem

private val BG     = Color(0xFF0D1117)
private val SURF   = Color(0xFF161B22)
private val CYAN   = Color(0xFF4FC3F7)
private val GREEN  = Color(0xFF56D364)
private val RED    = Color(0xFFFF7B72)
private val TEXT   = Color(0xFFE6EDF3)
private val SUB    = Color(0xFF6E7681)
private val PURPLE = Color(0xFFD2A8FF)
private val AMBER  = Color(0xFFFFA657)

private val BLUE = CYAN  // alias so Button refs still compile
private val ALL_ROLES = listOf("queen", "rook", "pawn")

@Composable
fun UsersScreen(
    uiState: MainUiState,
    onBack: () -> Unit,
    onCreateUser: (username: String, password: String, role: String, onDone: (String?) -> Unit) -> Unit,
    onDeleteUser: (String) -> Unit,
    onChangeRole: (String, String) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(BG).padding(24.dp)) {
        Spacer(Modifier.height(32.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Back", color = BLUE, fontSize = 14.sp) }
            Spacer(Modifier.weight(1f))
            Text("Users", color = TEXT, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showCreate = true }) { Text("+ Add", color = GREEN, fontSize = 14.sp) }
        }

        Spacer(Modifier.height(16.dp))

        if (uiState.isLoadingUsers) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = BLUE, modifier = Modifier.size(32.dp))
            }
        } else if (uiState.usersError.isNotEmpty()) {
            Text(uiState.usersError, color = RED, fontSize = 13.sp)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(uiState.users, key = { it.username }) { user ->
                    UserCard(
                        user = user,
                        actorRole = uiState.role,
                        onDelete = { onDeleteUser(user.username) },
                        onChangeRole = { newRole -> onChangeRole(user.username, newRole) },
                    )
                }
            }
        }
    }

    if (showCreate) {
        CreateUserDialog(
            actorRole = uiState.role,
            onCreate = { u, p, r, done -> onCreateUser(u, p, r, done) },
            onDismiss = { showCreate = false },
        )
    }
}

@Composable
private fun UserCard(
    user: UserItem,
    actorRole: String,
    onDelete: () -> Unit,
    onChangeRole: (String) -> Unit,
) {
    var showRoleMenu by remember { mutableStateOf(false) }
    val canManage = canManage(actorRole, user.role) && user.username != "king"

    val roleColor = when (user.role) {
        "king"  -> PURPLE
        "queen" -> AMBER
        "rook"  -> CYAN
        else    -> SUB
    }

    Surface(color = SURF, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(user.username, color = TEXT, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge(user.role, roleColor)
                    Badge(user.accountType, SUB)
                }
                if (user.createdBy.isNotBlank() && user.createdBy != "—") {
                    Text("by ${user.createdBy}", color = SUB, fontSize = 11.sp)
                }
            }
            if (canManage) {
                Box {
                    TextButton(onClick = { showRoleMenu = true }) {
                        Text("Role", color = BLUE, fontSize = 12.sp)
                    }
                    DropdownMenu(
                        expanded = showRoleMenu,
                        onDismissRequest = { showRoleMenu = false },
                    ) {
                        manageableRoles(actorRole).forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role) },
                                onClick = { showRoleMenu = false; onChangeRole(role) },
                                colors = MenuDefaults.itemColors(textColor = TEXT),
                            )
                        }
                    }
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = RED, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
private fun CreateUserDialog(
    actorRole: String,
    onCreate: (String, String, String, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(manageableRoles(actorRole).first()) }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var showRoleMenu by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(color = SURF, shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Create User", color = TEXT, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(value = username, onValueChange = { username = it.trim() },
                    label = { Text("Username", color = SUB) }, singleLine = true,
                    colors = dialogFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = password, onValueChange = { password = it },
                    label = { Text("Password", color = SUB) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = dialogFieldColors(), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))

                // Role selector
                Box {
                    OutlinedButton(onClick = { showRoleMenu = true }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TEXT)) {
                        Text("Role: $role", modifier = Modifier.weight(1f))
                        Text("▾", color = SUB)
                    }
                    DropdownMenu(expanded = showRoleMenu, onDismissRequest = { showRoleMenu = false }) {
                        manageableRoles(actorRole).forEach { r ->
                            DropdownMenuItem(
                                text = { Text(r) },
                                onClick = { role = r; showRoleMenu = false },
                                colors = MenuDefaults.itemColors(textColor = TEXT),
                            )
                        }
                    }
                }

                if (error.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = RED, fontSize = 12.sp)
                }

                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = SUB)) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) { error = "All fields required"; return@Button }
                            loading = true; error = ""
                            onCreate(username, password, role) { err ->
                                loading = false
                                if (err == null) onDismiss() else error = err
                            }
                        },
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(containerColor = BLUE, contentColor = BG),
                        modifier = Modifier.weight(1f),
                    ) { Text(if (loading) "..." else "Create") }
                }
            }
        }
    }
}

@Composable
private fun dialogFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TEXT, unfocusedTextColor = TEXT,
    focusedBorderColor = BLUE, unfocusedBorderColor = SUB, cursorColor = BLUE,
)

private fun canManage(actorRole: String, targetRole: String): Boolean {
    val rank = mapOf("king" to 4, "queen" to 3, "rook" to 2, "pawn" to 1)
    return (rank[actorRole] ?: 0) > (rank[targetRole] ?: 0)
}

private fun manageableRoles(actorRole: String): List<String> = when (actorRole) {
    "king"  -> listOf("queen", "rook", "pawn")
    "queen" -> listOf("rook", "pawn")
    else    -> emptyList()
}
