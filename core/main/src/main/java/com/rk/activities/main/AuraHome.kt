package com.rk.activities.main

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.activities.settings.SettingsActivity
import com.rk.agent.ChatMemory
import com.rk.agent.ChatSession
import com.rk.agent.SessionFlow
import com.rk.drawer.DrawerViewModel
import com.rk.file.FileWrapper
import com.rk.resources.drawables
import com.rk.settings.Settings
import com.rk.utils.toast
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Playground(val label: String, val tint: Color, val files: Map<String, String>)

private val playgrounds =
    listOf(
        Playground(
            "kotlin",
            Color(0xFF7F52FF),
            mapOf("Main.kt" to "fun main() {\n    println(\"Hello from AuraAi Kotlin!\")\n}\n"),
        ),
        Playground(
            "java",
            Color(0xFFE76F00),
            mapOf(
                "Main.java" to
                    "public class Main {\n    public static void main(String[] args) {\n" +
                        "        System.out.println(\"Hello from AuraAi Java!\");\n    }\n}\n"
            ),
        ),
        Playground(
            "python",
            Color(0xFF3776AB),
            mapOf("main.py" to "print(\"Hello from AuraAi Python!\")\n"),
        ),
        Playground(
            "c++",
            Color(0xFF00599C),
            mapOf(
                "main.cpp" to
                    "#include <iostream>\n\nint main() {\n    std::cout << \"Hello from AuraAi C++!\" << std::endl;\n    return 0;\n}\n"
            ),
        ),
    )

private val languageIcon: Map<String, Int> =
    mapOf(
        "kotlin" to drawables.kotlin,
        "java" to drawables.java,
        "python" to drawables.python,
        "c++" to drawables.cpp,
    )

private fun sandboxDir(androidPath: String): String =
    when {
        androidPath.startsWith("/storage/emulated/0/") ->
            "/sdcard/" + androidPath.removePrefix("/storage/emulated/0/")
        else -> androidPath
    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AuraHome(drawerState: DrawerState, drawerViewModel: DrawerViewModel) {
    val context = LocalContext.current
    val activity = context as android.app.Activity
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val mono = FontFamily.Monospace

    var tab by remember { mutableIntStateOf(0) }
    var pendingSave by remember { mutableStateOf<ChatSession?>(null) }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) { SessionFlow.warmUpSandbox() }
    }

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$",
                fontFamily = mono,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = colorScheme.secondary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "AuraAi",
                style =
                    TextStyle(
                        brush =
                            Brush.linearGradient(
                                listOf(colorScheme.primary, colorScheme.secondary)
                            )
                    ).merge(MaterialTheme.typography.headlineMedium),
            )
            BlinkingCursor(tint = colorScheme.secondary)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                Icon(painterResource(drawables.outline_folder), null, Modifier.size(20.dp))
            }
            IconButton(
                onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }
            ) {
                Icon(painterResource(drawables.settings), null, Modifier.size(20.dp))
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(Modifier.padding(horizontal = 18.dp)) {
            TabBar(tabs = listOf("projects", "chats"), selected = tab, onSelect = { tab = it })
        }

        Spacer(Modifier.height(12.dp))

        if (tab == 0) {
            ProjectsTab(drawerViewModel, mono) { pendingSave = it }
        } else {
            ChatsTab(mono)
        }
    }

    pendingSave?.let { session ->
        SaveSessionDialog(
            session = session,
            onDismiss = { pendingSave = null },
            onSave = { name ->
                val target = session
                pendingSave = null
                scope.launch {
                    runCatching { ChatMemory.renameSession(target.id, name) }
                        .onFailure { toast("could not save: ${it.message}") }
                }
            },
            onDiscard = {
                val target = session
                pendingSave = null
                scope.launch {
                    ChatMemory.deleteSessions(listOf(target.id))
                        .onFailure { toast("could not delete: ${it.message}") }
                }
            },
        )
    }
}

@Composable
private fun BlinkingCursor(tint: Color) {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by
        transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.15f,
            animationSpec =
                infiniteRepeatable(animation = tween(durationMillis = 650), repeatMode = RepeatMode.Reverse),
            label = "cursorAlpha",
        )
    Spacer(Modifier.width(4.dp))
    Text(
        "▊",
        fontFamily = FontFamily.Monospace,
        fontSize = 22.sp,
        color = tint.copy(alpha = alpha),
    )
}

@Composable
private fun TabBar(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    val indicatorOffset by animateDpAsState(targetValue = (selected * 90).dp, label = "tab")
    Column {
        Row {
            tabs.forEachIndexed { index, label ->
                Box(
                    modifier =
                        Modifier.width(90.dp).clickable { onSelect(index) }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (selected == index) FontWeight.Bold else FontWeight.Normal,
                        color =
                            if (selected == index) colorScheme.onSurface
                            else colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier.offset(x = indicatorOffset + 17.dp)
                    .width(56.dp)
                    .height(3.dp)
                    .background(colorScheme.primary, RoundedCornerShape(2.dp))
        )
    }
}

private fun openProject(scope: kotlinx.coroutines.CoroutineScope, drawerViewModel: DrawerViewModel, path: String) {
    scope.launch { drawerViewModel.addFileTreeTab(FileWrapper(File(path)), save = true) }
}

private fun createPlayground(
    scope: kotlinx.coroutines.CoroutineScope,
    drawerViewModel: DrawerViewModel,
    playground: Playground,
) {
    scope.launch {
        val base = File(Environment.getExternalStorageDirectory(), "AuraAi-Playgrounds")
        val dir =
            withContext(Dispatchers.IO) {
                var d = File(base, playground.label)
                var counter = 2
                while (d.exists()) {
                    d = File(base, "${playground.label}-$counter")
                    counter++
                }
                d.mkdirs()
                playground.files.forEach { (name, content) -> File(d, name).writeText(content) }
                d
            }
        drawerViewModel.addFileTreeTab(FileWrapper(dir), save = true)
        toast("${playground.label} playground created")
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectsTab(
    drawerViewModel: DrawerViewModel,
    mono: FontFamily,
    onSessionEnded: (ChatSession?) -> Unit,
) {
    val context = LocalContext.current
    val activity = context as android.app.Activity
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme

    var branches by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var chatDirByName by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var latest by remember { mutableStateOf<ChatSession?>(null) }
    var showPlaygrounds by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    var recent by remember { mutableStateOf(listOf<String>()) }
    var all by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        recent = Settings.recent_projects.split("\n").filter { it.isNotBlank() }.distinct()
        all = Settings.all_projects.split("\n").filter { it.isNotBlank() }.distinct()

        launch(Dispatchers.IO) {
            val sessions = runCatching { ChatMemory.listSessions() }.getOrDefault(emptyList())
            latest = sessions.firstOrNull()
            chatDirByName =
                sessions.associate { File(it.directory).name to it.directory }
                    .filterKeys { it.isNotBlank() }

            val dirs = (recent + all).distinct()
            if (dirs.isNotEmpty()) {
                val dollar = "${'$'}"
                val script = StringBuilder("while IFS= read -r d; do ")
                script.append("b=${dollar}(git -C \"${dollar}d\" --no-optional-locks branch --show-current 2>/dev/null); ")
                script.append("printf '%s\\t%s\\n' \"${dollar}d\" \"${dollar}b\"; done <<'EOF'\n")
                script.append(dirs.joinToString("\n")).append("\nEOF")
                val res =
                    runCatching {
                        com.rk.exec.ShellUtils.runUbuntu(
                            workingDir = null,
                            "bash",
                            "-lc",
                            script.toString(),
                            timeoutSeconds = 60L,
                        )
                    }.getOrNull()
                if (res != null && res.exitCode == 0) {
                    branches =
                        res.output.lines().mapNotNull { line ->
                            val p = line.split("\t", limit = 2)
                            if (p.size == 2 && p[1].isNotBlank()) p[0] to p[1] else null
                        }.toMap()
                }
            }
        }
    }

    val ordered = (recent + all).distinct()

    Column(
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroCard(
                title = "resume chat",
                subtitle =
                    latest?.let { "${it.title.take(28)} · ${ChatMemory.formatRelativeTime(it.updatedMs)}" }
                        ?: "no chats yet",
                icon = drawables.comment,
                enabled = latest != null,
                modifier = Modifier.weight(1f),
            ) {
                latest?.let { ChatMemory.continueSession(activity, it) }
            }
            HeroCard(
                title = "new chat",
                subtitle = "start fresh ai session",
                icon = drawables.add,
                enabled = true,
                modifier = Modifier.weight(1f),
            ) {
                SessionFlow.watchNewChat(activity, null) { onSessionEnded(it) }
            }
        }

        Spacer(Modifier.height(24.dp))

        SectionHeader(title = "projects")
        Spacer(Modifier.height(10.dp))

        if (ordered.isEmpty()) {
            EmptyHint("no projects yet — open a folder from the drawer")
        } else {
            ordered.forEach { path ->
                val dirName = File(path).name.ifBlank { path }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colorScheme.surfaceContainer,
                    modifier =
                        Modifier.fillMaxWidth()
                            .combinedClickable(
                                onClick = { openProject(scope, drawerViewModel, path) },
                                onLongClick = { menuFor = path },
                            ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painterResource(drawables.folder),
                            null,
                            tint = colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                dirName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                path,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = mono,
                                color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        branches[path]?.let { branch ->
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painterResource(drawables.branch),
                                        null,
                                        tint = colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(11.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        branch,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = mono,
                                        color = colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Box(contentAlignment = Alignment.TopEnd) {
                    DropdownMenu(expanded = menuFor == path, onDismissRequest = { menuFor = null }) {
                        DropdownMenuItem(
                            text = { Text("open") },
                            leadingIcon = {
                                Icon(painterResource(drawables.outline_folder), null, Modifier.size(16.dp))
                            },
                            onClick = {
                                menuFor = null
                                openProject(scope, drawerViewModel, path)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("new ai chat") },
                            leadingIcon = {
                                Icon(painterResource(drawables.comment), null, Modifier.size(16.dp))
                            },
                            onClick = {
                                menuFor = null
                                SessionFlow.watchNewChat(activity, chatDirByName[dirName] ?: sandboxDir(path)) {
                                    onSessionEnded(it)
                                }
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("remove from list") },
                            leadingIcon = {
                                Icon(painterResource(drawables.close), null, Modifier.size(16.dp))
                            },
                            onClick = {
                                menuFor = null
                                Settings.recent_projects =
                                    Settings.recent_projects.split("\n")
                                        .filter { it.isNotBlank() && it != path }
                                        .joinToString("\n")
                                Settings.all_projects =
                                    Settings.all_projects.split("\n")
                                        .filter { it.isNotBlank() && it != path }
                                        .joinToString("\n")
                                recent = Settings.recent_projects.split("\n").filter { it.isNotBlank() }
                                all = Settings.all_projects.split("\n").filter { it.isNotBlank() }
                                toast("removed from home")
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionHeader(title = "playgrounds", modifier = Modifier.weight(1f))
            IconButton(onClick = { showPlaygrounds = !showPlaygrounds }) {
                Icon(
                    painterResource(drawables.chevron_down),
                    null,
                    Modifier.size(18.dp).graphicsLayer { rotationZ = if (showPlaygrounds) 180f else 0f },
                )
            }
        }
        AnimatedVisibility(visible = showPlaygrounds) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                playgrounds.forEach { playground ->
                    PlaygroundChip(playground) { createPlayground(scope, drawerViewModel, playground) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatsTab(mono: FontFamily) {
    val context = LocalContext.current
    val activity = context as android.app.Activity
    val scope = rememberCoroutineScope()

    var sessions by remember { mutableStateOf<List<ChatSession>?>(null) }
    var counts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var query by remember { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var renameFor by remember { mutableStateOf<ChatSession?>(null) }
    var deleteFor by remember { mutableStateOf<ChatSession?>(null) }
    var pinVersion by remember { mutableIntStateOf(0) }

    suspend fun load() {
        sessions = runCatching { ChatMemory.listSessions() }.getOrDefault(emptyList())
        counts = runCatching { ChatMemory.messageCounts() }.getOrDefault(emptyMap())
    }

    LaunchedEffect(Unit) { load() }

    val pinned = remember(pinVersion) { Settings.pinned_sessions.split("\n").filter { it.isNotBlank() }.toSet() }

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text("search chats…", fontFamily = mono, style = MaterialTheme.typography.bodySmall)
            },
            leadingIcon = { Icon(painterResource(drawables.search), null, Modifier.size(18.dp)) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(painterResource(drawables.close), null, Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
        )

        Spacer(Modifier.height(12.dp))

        val list = sessions
        when {
            list == null ->
                Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            list.isEmpty() -> EmptyHint("no chats yet — start one from the projects tab")
            else -> {
                val filtered =
                    list.filter {
                        query.isBlank() ||
                            it.title.contains(query, ignoreCase = true) ||
                            it.directory.contains(query, ignoreCase = true)
                    }
                val sorted =
                    filtered.sortedWith(
                        compareByDescending<ChatSession> { it.id in pinned }
                            .thenByDescending { it.updatedMs }
                    )
                if (sorted.isEmpty()) {
                    EmptyHint("nothing matches \"$query\"")
                } else {
                    Column(
                        Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)
                    ) {
                        sorted.forEach { session ->
                            SessionRow(
                                session = session,
                                messageCount = counts[session.id] ?: 0,
                                isPinned = session.id in pinned,
                                mono = mono,
                                onClick = { ChatMemory.continueSession(activity, session) },
                                onLongClick = { menuFor = session.id },
                            )
                            Box(contentAlignment = Alignment.TopEnd) {
                                DropdownMenu(
                                    expanded = menuFor == session.id,
                                    onDismissRequest = { menuFor = null },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(if (session.id in pinned) "unpin" else "pin") },
                                        leadingIcon = {
                                            Icon(painterResource(drawables.push_pin), null, Modifier.size(16.dp))
                                        },
                                        onClick = {
                                            menuFor = null
                                            val set = pinned.toMutableSet()
                                            if (session.id in set) set.remove(session.id) else set.add(session.id)
                                            Settings.pinned_sessions = set.joinToString("\n")
                                            pinVersion++
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("rename") },
                                        leadingIcon = {
                                            Icon(painterResource(drawables.edit), null, Modifier.size(16.dp))
                                        },
                                        onClick = { menuFor = null; renameFor = session },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("export markdown") },
                                        leadingIcon = {
                                            Icon(painterResource(drawables.send), null, Modifier.size(16.dp))
                                        },
                                        onClick = {
                                            menuFor = null
                                            scope.launch {
                                                runCatching { ChatMemory.exportMarkdown(session) }
                                                    .onSuccess { markdown ->
                                                        val send =
                                                            Intent(Intent.ACTION_SEND).apply {
                                                                type = "text/plain"
                                                                putExtra(Intent.EXTRA_SUBJECT, session.title)
                                                                putExtra(Intent.EXTRA_TEXT, markdown)
                                                            }
                                                        context.startActivity(
                                                            Intent.createChooser(send, "export chat")
                                                        )
                                                    }
                                                    .onFailure { toast("export failed: ${it.message}") }
                                            }
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("delete") },
                                        leadingIcon = {
                                            Icon(painterResource(drawables.delete), null, Modifier.size(16.dp))
                                        },
                                        onClick = { menuFor = null; deleteFor = session },
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }

    renameFor?.let { session ->
        var name by remember(session.id) { mutableStateOf(session.title) }
        AlertDialog(
            onDismissRequest = { renameFor = null },
            title = { Text("rename chat", fontFamily = mono) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = session
                        renameFor = null
                        scope.launch {
                            runCatching { ChatMemory.renameSession(target.id, name) }
                                .onSuccess { load() }
                                .onFailure { toast("rename failed: ${it.message}") }
                        }
                    }
                ) { Text("save") }
            },
            dismissButton = { TextButton(onClick = { renameFor = null }) { Text("cancel") } },
        )
    }

    deleteFor?.let { session ->
        AlertDialog(
            onDismissRequest = { deleteFor = null },
            title = { Text("delete chat?", fontFamily = mono) },
            text = {
                Text(
                    "\"${session.title}\" and all its messages will be removed permanently.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val target = session
                        deleteFor = null
                        scope.launch {
                            ChatMemory.deleteSessions(listOf(target.id))
                                .onSuccess { load() }
                                .onFailure { toast("delete failed: ${it.message}") }
                        }
                    }
                ) { Text("delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleteFor = null }) { Text("cancel") } },
        )
    }
}

@Composable
private fun SaveSessionDialog(
    session: ChatSession,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onDiscard: () -> Unit,
) {
    var name by remember { mutableStateOf(defaultSessionName(session)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("save this chat?") },
        text = {
            Column {
                Text(
                    "keep it in your history, or delete it forever.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("session name") },
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("save") } },
        dismissButton = {
            TextButton(onClick = onDiscard) {
                Text("don't save", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

private fun defaultSessionName(session: ChatSession): String {
    val dir = File(session.directory).name.ifBlank { "chat" }
    return "$dir ${
        java.text.SimpleDateFormat("MMM d HH:mm", java.util.Locale.US)
            .format(java.util.Date(session.createdMs))
    }"
}

@Composable
private fun HeroCard(
    title: String,
    subtitle: String,
    icon: Int,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(14.dp),
        color =
            if (enabled) colorScheme.primaryContainer.copy(alpha = 0.55f)
            else colorScheme.surfaceContainer,
        modifier = modifier.height(76.dp).clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(icon),
                null,
                tint =
                    if (enabled) colorScheme.onPrimaryContainer
                    else colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (enabled) colorScheme.onPrimaryContainer
                        else colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(
    session: ChatSession,
    messageCount: Int,
    isPinned: Boolean,
    mono: FontFamily,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(12.dp),
        color =
            if (isPinned) colorScheme.surfaceContainerHigh else colorScheme.surfaceContainer,
        modifier =
            Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(if (isPinned) drawables.push_pin else drawables.comment),
                null,
                tint =
                    if (isPinned) colorScheme.primary
                    else colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isPinned) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        File(session.directory).name.ifBlank { "/" },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = mono,
                        color = colorScheme.primary.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "· ${ChatMemory.formatRelativeTime(session.updatedMs)} · $messageCount msg · ${ChatMemory.formatTokens(session.tokensInput + session.tokensOutput)} tok",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                painterResource(drawables.more_vert),
                null,
                tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp).clickable(onClick = onLongClick),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontFamily = FontFamily.Monospace,
        letterSpacing = 2.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        textAlign = TextAlign.Start,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
    )
}

@Composable
private fun PlaygroundChip(playground: Playground, onClick: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = colorScheme.surfaceContainer,
        border =
            androidx.compose.foundation.BorderStroke(1.dp, playground.tint.copy(alpha = 0.35f)),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painterResource(languageIcon[playground.label]!!),
                null,
                tint = playground.tint,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "+ ${playground.label}",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = playground.tint,
            )
        }
    }
}
