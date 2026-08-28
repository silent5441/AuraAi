package com.rk.terminal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.res.painterResource
import com.rk.resources.drawables
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.rk.resources.strings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SessionHistoryScreen(navController: NavController) {
    var searchText by remember { mutableStateOf("") }
    var sessions by remember { mutableStateOf(SessionHistory.getAllSessions()) }
    var showClearAllDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var sessionToDelete by remember { mutableStateOf<SessionMetadata?>(null) }

    // Clear All Confirmation
    if (showClearAllDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllDialog = false },
            title = { Text("Clear Session History") },
            text = { Text("Are you sure you want to clear all session history? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        SessionHistory.deleteAllSessions()
                        sessions = emptyList()
                        showClearAllDialog = false
                    }
                ) { Text("Clear All") }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Delete Session Confirmation
    if (showDeleteDialog && sessionToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                sessionToDelete = null
            },
            title = { Text("Delete Session") },
            text = { Text("Delete session '${sessionToDelete?.name}' from history?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionToDelete?.let {
                            SessionHistory.deleteSession(it.id)
                            sessions = SessionHistory.searchSessions(searchText)
                        }
                        showDeleteDialog = false
                        sessionToDelete = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        sessionToDelete = null
                    }
                ) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Session History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${sessions.size} sessions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showClearAllDialog = true }) {
                    Icon(
                        painter = painterResource(drawables.delete),
                        contentDescription = "Clear all history"
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = {
                    searchText = it
                    sessions = SessionHistory.searchSessions(it)
                },
                placeholder = { Text("Search sessions by name...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (sessions.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(64.dp))
                    Text(
                        if (searchText.isBlank()) {
                            "No sessions in history yet.\n\nStart a new terminal session and it will appear here."
                        } else {
                            "No sessions match '$searchText'."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val dateFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.US)

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(sessions) { session ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clickable {
                                    // Restore this session - reuse the original session ID to maintain continuity
                                    val activity = com.rk.activities.terminal.Terminal.instance
                                    if (activity != null) {
                                        val binder = activity.sessionBinder?.get()
                                        if (binder != null) {
                                            val existingSession = binder.getSession(session.id)
                                            if (existingSession != null) {
                                                // Session is still running, switch to it
                                                activity.changeSession(session.id)
                                            } else {
                                                // Create a new session with the original ID and name
                                                binder.createSession(
                                                    session.id,
                                                    TerminalBackEnd(),
                                                    activity,
                                                    session.name,
                                                    session.isSandbox,
                                                )
                                                activity.changeSession(session.id)
                                            }
                                        }
                                    }
                                },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        session.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        if (session.isSandbox) {
                                            "PRoot/Ubuntu"
                                        } else {
                                            "Native Shell"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (session.isSandbox) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.tertiary
                                        }
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        session.workingDir.ifBlank { "/" },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "Last used: ${dateFormat.format(Date(session.lastUsedAt))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        sessionToDelete = session
                                        showDeleteDialog = true
                                    },
                                    modifier = Modifier.width(36.dp).height(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete session",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                        modifier = Modifier.width(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
