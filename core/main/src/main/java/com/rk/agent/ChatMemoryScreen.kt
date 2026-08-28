package com.rk.agent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.components.InfoBlock
import com.rk.components.compose.preferences.base.PreferenceLayoutLazyColumn
import com.rk.exec.isTerminalInstalled
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.theme.greenStatus
import kotlinx.coroutines.launch

@Composable
fun ChatMemoryScreen(activity: android.app.Activity) {
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var dbMissing by remember { mutableStateOf(false) }
    var agentRunning by remember { mutableStateOf(false) }
    var projects by remember { mutableStateOf<List<ProjectMemory>>(emptyList()) }
    var refreshTick by remember { mutableStateOf(0) }

    // pending deletions awaiting confirmation
    var pendingSessionDelete by remember { mutableStateOf<ChatSession?>(null) }
    var pendingProjectDelete by remember { mutableStateOf<ProjectMemory?>(null) }

    suspend fun reload() {
        loading = true
        if (!isTerminalInstalled()) {
            dbMissing = true
            loading = false
            return
        }
        dbMissing = !ChatMemory.hasDatabase()
        if (!dbMissing) {
            agentRunning = ChatMemory.isOpencodeRunning()
            projects = runCatching { ChatMemory.listProjects() }.getOrDefault(emptyList())
        }
        loading = false
    }

    LaunchedEffect(refreshTick) { reload() }

    fun deleteSessions(ids: List<String>, onDone: () -> Unit = {}) {
        scope.launch {
            ChatMemory.deleteSessions(ids)
            onDone()
            refreshTick++
        }
    }

    PreferenceLayoutLazyColumn(
        label = stringResource(strings.memory_title),
        backArrowVisible = true,
        actions = {
            IconButton(onClick = { refreshTick++ }) {
                Icon(painterResource(drawables.refresh), contentDescription = stringResource(strings.setup_refresh))
            }
        },
    ) {
        if (!isTerminalInstalled() || dbMissing) {
            item {
                InfoBlock(
                    icon = { Icon(painterResource(drawables.info), null) },
                    text =
                        stringResource(
                            if (!isTerminalInstalled()) strings.agent_needs_terminal_desc
                            else strings.memory_db_missing
                        ),
                    warning = true,
                )
            }
            return@PreferenceLayoutLazyColumn
        }

        if (loading) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Text(
                        stringResource(strings.setup_checking),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            return@PreferenceLayoutLazyColumn
        }

        if (agentRunning) {
            item {
                InfoBlock(
                    icon = { Icon(painterResource(drawables.info), null) },
                    text = stringResource(strings.memory_opencode_running),
                    warning = true,
                )
            }
        }

        if (projects.isEmpty()) {
            item {
                InfoBlock(
                    icon = { Icon(painterResource(drawables.comment), null) },
                    text = stringResource(strings.memory_empty),
                    warning = false,
                )
            }
            return@PreferenceLayoutLazyColumn
        }

        items(projects.size) { index ->
            val project = projects[index]
            ProjectCard(
                project = project,
                deletionBlocked = agentRunning,
                onContinueLatest = { ChatMemory.openProjectChat(activity, project.directory, continueLatest = true) },
                onDeleteAll = { pendingProjectDelete = project },
                onDeleteSession = { pendingSessionDelete = it },
                onContinueSession = { ChatMemory.continueSession(activity, it) },
            )
        }
    }

    pendingSessionDelete?.let { session ->
        ConfirmDialog(
            title = stringResource(strings.memory_delete),
            text = stringResource(strings.memory_delete_session_confirm) + "\n\n" + session.title,
            onConfirm = {
                pendingSessionDelete = null
                deleteSessions(listOf(session.id))
            },
            onDismiss = { pendingSessionDelete = null },
        )
    }

    pendingProjectDelete?.let { project ->
        ConfirmDialog(
            title = stringResource(strings.memory_delete_all),
            text =
                stringResource(strings.memory_delete_all_confirm, project.sessions.size) +
                    "\n\n" +
                    project.directory,
            onConfirm = {
                pendingProjectDelete = null
                deleteSessions(project.sessions.map { it.id })
            },
            onDismiss = { pendingProjectDelete = null },
        )
    }
}

@Composable
private fun ProjectCard(
    project: ProjectMemory,
    deletionBlocked: Boolean,
    onContinueLatest: () -> Unit,
    onDeleteAll: () -> Unit,
    onDeleteSession: (ChatSession) -> Unit,
    onContinueSession: (ChatSession) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded },
            ) {
                Icon(
                    painterResource(drawables.folder),
                    null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = project.directory.substringAfterLast('/').ifBlank { project.directory },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            stringResource(strings.memory_project_sessions, project.sessions.size) +
                                " · " +
                                ChatMemory.formatRelativeTime(project.lastUpdatedMs),
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                }
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        painterResource(if (expanded) drawables.chevron_up else drawables.chevron_down),
                        null,
                        modifier = Modifier.size(18.dp),
                        tint = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.size(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onContinueLatest,
                        colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    ) {
                        Icon(painterResource(drawables.run), null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(strings.memory_continue), fontSize = 13.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        enabled = !deletionBlocked,
                        onClick = onDeleteAll,
                    ) {
                        Icon(painterResource(drawables.close), null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(strings.memory_delete_all), fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.size(4.dp))
                project.sessions.forEach { session ->
                    SessionRow(
                        session = session,
                        canDelete = !deletionBlocked,
                        onContinue = { onContinueSession(session) },
                        onDelete = { onDeleteSession(session) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: ChatSession,
    canDelete: Boolean,
    onContinue: () -> Unit,
    onDelete: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onContinue)
                .padding(start = 30.dp, top = 6.dp, bottom = 6.dp, end = 2.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text =
                    "${session.modelId} · ${ChatMemory.formatRelativeTime(session.updatedMs)}" +
                        if (session.tokensOutput > 0) " · ${ChatMemory.formatTokens(session.tokensOutput)} out" else "",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onContinue, modifier = Modifier.size(30.dp)) {
            Icon(
                painterResource(drawables.run),
                stringResource(strings.memory_continue),
                tint = colorScheme.greenStatus,
                modifier = Modifier.size(17.dp),
            )
        }
        IconButton(onClick = onDelete, enabled = canDelete, modifier = Modifier.size(30.dp)) {
            Icon(
                painterResource(drawables.close),
                stringResource(strings.memory_delete),
                tint = if (canDelete) Color(0xFFE57373) else colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(strings.delete), color = Color(0xFFE57373))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(strings.cancel)) } },
    )
}
