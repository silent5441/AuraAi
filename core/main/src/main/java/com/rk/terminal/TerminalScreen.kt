package com.rk.terminal

import android.graphics.Typeface
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberDrawerState
import com.rk.components.compose.appbars.TerminalHeader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.rk.agent.ChatMemoryScreen
import com.rk.activities.settings.SettingsRoutes
import com.rk.activities.terminal.Terminal
import com.rk.animations.NavigationAnimationTransitions
import com.rk.editor.FontCache
import com.rk.exec.pendingCommand
import com.rk.file.child
import com.rk.file.sandboxDir
import com.rk.resources.drawables
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.settings.editor.DEFAULT_TERMINAL_FONT_PATH
import com.rk.settings.editor.TerminalFontScreen
import com.rk.settings.terminal.DEFAULT_TERMINAL_EXTRA_KEYS
import com.rk.settings.terminal.SettingsTerminalScreen
import com.rk.settings.terminal.TerminalCheckScreen
import com.rk.settings.terminal.TerminalExtraKeys
import com.rk.terminal.setup.SetupScreen
import com.rk.terminal.virtualkeys.VirtualKeysConstants
import com.rk.terminal.virtualkeys.VirtualKeysInfo
import com.rk.terminal.virtualkeys.VirtualKeysListener
import com.rk.terminal.virtualkeys.VirtualKeysView
import com.rk.theme.LocalThemeHolder
import com.rk.theme.ThemeHolder
import com.rk.utils.dpToPx
import com.rk.utils.toast
import com.termux.terminal.TerminalColors
import com.termux.terminal.TextStyle
import com.termux.view.TerminalView
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.Properties

var terminalView = WeakReference<TerminalView?>(null)
var virtualKeysView = WeakReference<VirtualKeysView?>(null)

@Composable
fun TerminalScreen(modifier: Modifier = Modifier, terminalActivity: Terminal) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "terminal",
        enterTransition = { NavigationAnimationTransitions.enterTransition },
        exitTransition = { NavigationAnimationTransitions.exitTransition },
        popEnterTransition = { NavigationAnimationTransitions.popEnterTransition },
        popExitTransition = { NavigationAnimationTransitions.popExitTransition },
    ) {
        composable("terminal") {
            TerminalScreenInternal(terminalActivity = terminalActivity, navController = navController)
        }
        composable(SettingsRoutes.TerminalSettings.route) { SettingsTerminalScreen(navController) }
        composable(SettingsRoutes.TerminalFontScreen.route) { TerminalFontScreen() }
        composable(SettingsRoutes.TerminalExtraKeys.route) { TerminalExtraKeys() }
        composable(SettingsRoutes.TerminalCheck.route) { TerminalCheckScreen() }
        composable("aura_setups") { SetupScreen(terminalActivity) }
        composable("aura_chat_memory") { ChatMemoryScreen(terminalActivity) }
        composable("session_history") { SessionHistoryScreen(navController) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreenInternal(modifier: Modifier = Modifier, terminalActivity: Terminal, navController: NavController) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface.toArgb()
    val isDarkMode = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val currentTheme = LocalThemeHolder.current
    
    var showNewSessionDialog by remember { mutableStateOf(false) }
    var showCloseSessionDialog by remember { mutableStateOf(false) }
    var sessionToClose by remember { mutableStateOf<String?>(null) }
    var newSessionName by remember { mutableStateOf("") }
    var newSessionUseSandbox by remember { mutableStateOf(Settings.sandbox) }

    DisposableEffect(Unit) { onDispose { keyboardController?.hide() } }

    Box(modifier = Modifier.imePadding()) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val configuration = LocalConfiguration.current
        val screenWidthDp = configuration.screenWidthDp
        val drawerWidth = (screenWidthDp * 0.84).dp

        BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

        // New Session Dialog
        if (showNewSessionDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showNewSessionDialog = false
                    newSessionName = ""
                    newSessionUseSandbox = Settings.sandbox
                },
                title = { Text("New Terminal Session") },
                text = {
                    Column {
                        TextField(
                            value = newSessionName,
                            onValueChange = { newSessionName = it },
                            label = { Text("Session Name") },
                            placeholder = { Text("e.g., Dev Server, Git, etc.") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = newSessionUseSandbox,
                                onCheckedChange = { newSessionUseSandbox = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Use PRoot/Ubuntu Sandbox")
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val name = newSessionName.ifBlank { "Session ${terminalActivity.sessionBinder?.get()?.getService()?.sessionList?.size?.plus(1) ?: 1}" }
                            val sessionId = "session_${System.currentTimeMillis()}"
                            
                            terminalView.get()?.let {
                                val client = TerminalBackEnd()
                                terminalActivity.sessionBinder
                                    ?.get()!!
                                    .createSession(sessionId, client, terminalActivity, name, newSessionUseSandbox)
                            }
                            
                            showNewSessionDialog = false
                            newSessionName = ""
                            newSessionUseSandbox = Settings.sandbox
                        }
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showNewSessionDialog = false
                            newSessionName = ""
                            newSessionUseSandbox = Settings.sandbox
                        }
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }

        // Close Session Confirmation Dialog
        if (showCloseSessionDialog && sessionToClose != null) {
            AlertDialog(
                onDismissRequest = { 
                    showCloseSessionDialog = false
                    sessionToClose = null
                },
                title = { Text("Close Session") },
                text = {
                    Text("Are you sure you want to close this session? Any unsaved work may be lost.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            sessionToClose?.let { sessionId ->
                                val service = terminalActivity.sessionBinder?.get()?.getService() ?: return@TextButton
                                
                                if (sessionId == service.currentSession.value) {
                                    val index = service.sessionList.indexOf(sessionId)
                                    val neighbor = service.sessionList.getOrNull(index - 1) ?: service.sessionList.getOrNull(index + 1)
                                    neighbor?.let { terminalActivity.changeSession(it) }
                                }
                                terminalActivity.sessionBinder?.get()?.terminateSession(sessionId)
                                
                                if (service.sessionList.isEmpty()) {
                                    terminalActivity.finish()
                                    service.actionExit()
                                }
                            }
                            showCloseSessionDialog = false
                            sessionToClose = null
                        }
                    ) {
                        Text("Close")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showCloseSessionDialog = false
                            sessionToClose = null
                        }
                    ) {
                        Text("Cancel")
                    }
                },
            )
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            gesturesEnabled = drawerState.isOpen,
            drawerContent = { TerminalDrawer(drawerWidth, terminalActivity, navController) },
            content = {
                Scaffold(
                    topBar = {
                        TerminalHeader(
                            title = stringResource(strings.terminal),
                            actions = {
                                IconButton(onClick = { navController.navigate("aura_chat_memory") }) {
                                    Icon(
                                        painter = painterResource(drawables.comment),
                                        contentDescription = stringResource(strings.memory_title),
                                    )
                                }
                                IconButton(onClick = { navController.navigate("aura_setups") }) {
                                    Icon(
                                        painter = painterResource(drawables.widgets),
                                        contentDescription = stringResource(strings.setup_title),
                                    )
                                }
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Default.Menu, null)
                                }
                            },
                        )
                    }
                ) { paddingValues ->
                    Column(modifier = Modifier.padding(paddingValues)) {
                        TerminalStatusBar(terminalActivity)
                        SessionTabStrip(
                            terminalActivity = terminalActivity,
                            onShowNewSessionDialog = { showNewSessionDialog = true },
                            onShowCloseSessionDialog = { sessionId ->
                                if (Settings.confirm_close_session) {
                                    sessionToClose = sessionId
                                    showCloseSessionDialog = true
                                } else {
                                    val service = terminalActivity.sessionBinder?.get()?.getService() ?: return@SessionTabStrip
                                    val current = service.currentSession.value
                                    
                                    if (sessionId == current) {
                                        val index = service.sessionList.indexOf(sessionId)
                                        val neighbor = service.sessionList.getOrNull(index - 1) ?: service.sessionList.getOrNull(index + 1)
                                        neighbor?.let { terminalActivity.changeSession(it) }
                                    }
                                    terminalActivity.sessionBinder?.get()?.terminateSession(sessionId)
                                    
                                    if (service.sessionList.isEmpty()) {
                                        terminalActivity.finish()
                                        service.actionExit()
                                    }
                                }
                            }
                        )
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .padding(top = 6.dp)
                        ) {
                            val terminalShape = RoundedCornerShape(10.dp)
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .clip(terminalShape)
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, terminalShape)
                            ) {
                                TerminalView(isDarkMode, currentTheme, surfaceColor, onSurfaceColor, terminalActivity)
                            }
                        }
                        TerminalFooter(terminalActivity)

                        val pagerState = rememberPagerState(pageCount = { 2 })
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().height(75.dp)) { page ->
                            when (page) {
                                0 -> {
                                    terminalView.get()?.requestFocus()
                                    AndroidView(
                                        factory = { context ->
                                            VirtualKeysView(context, null).apply {
                                                virtualKeysView = WeakReference(this)
                                                virtualKeysViewClient =
                                                    terminalView.get()?.mTermSession?.let { VirtualKeysListener(it) }

                                                buttonTextColor = onSurfaceColor

                                                runCatching {
                                                        reload(
                                                            VirtualKeysInfo(
                                                                Settings.terminal_extra_keys,
                                                                "",
                                                                VirtualKeysConstants.CONTROL_CHARS_ALIASES,
                                                            )
                                                        )
                                                    }
                                                    .onFailure {
                                                        toast(strings.invalid_terminal_extra_keys)
                                                        reload(
                                                            VirtualKeysInfo(
                                                                DEFAULT_TERMINAL_EXTRA_KEYS,
                                                                "",
                                                                VirtualKeysConstants.CONTROL_CHARS_ALIASES,
                                                            )
                                                        )
                                                    }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().height(75.dp),
                                    )
                                }

                                1 -> {
                                    var text by rememberSaveable { mutableStateOf("") }
                                    val focusRequester = remember { FocusRequester() }

                                    TextField(
                                        value = text,
                                        onValueChange = { text = it },
                                        maxLines = 1,
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions =
                                            KeyboardActions(
                                                onDone = {
                                                    if (text.isEmpty()) {
                                                        // Dispatch enter key events if text is empty
                                                        val eventDown =
                                                            KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)
                                                        val eventUp =
                                                            KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER)
                                                        terminalView.get()?.dispatchKeyEvent(eventDown)
                                                        terminalView.get()?.dispatchKeyEvent(eventUp)
                                                    } else {
                                                        terminalView.get()?.currentSession?.write(text)
                                                        text = ""
                                                    }
                                                }
                                            ),
                                        modifier = Modifier.fillMaxWidth().height(75.dp).focusRequester(focusRequester),
                                    )

                                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                                }
                            }
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun SessionTabStrip(
    terminalActivity: Terminal,
    onShowNewSessionDialog: () -> Unit = {},
    onShowCloseSessionDialog: (String) -> Unit = {},
) {
    val service = terminalActivity.sessionBinder?.get()?.getService() ?: return
    val sessions = service.sessionList
    val current = service.currentSession.value
    val colorScheme = MaterialTheme.colorScheme

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(sessions.size) { index ->
            val sessionId = sessions[index]
            val isSelected = sessionId == current
            val sessionMetadata = service.getSessionMetadata(sessionId)
            val displayName = sessionMetadata?.name ?: sessionId
            
            Surface(
                shape = RoundedCornerShape(50),
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainer
                    },
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        } else {
                            colorScheme.outlineVariant.copy(alpha = 0.6f)
                        },
                    ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                ) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier =
                            Modifier.clickable { terminalActivity.changeSession(sessionId) },
                    )
                    IconButton(onClick = { onShowCloseSessionDialog(sessionId) }, modifier = Modifier.size(20.dp)) {
                        Icon(
                            painter = painterResource(drawables.close),
                            contentDescription = stringResource(strings.delete),
                            tint = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }
        item {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = androidx.compose.foundation.BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
            ) {
                IconButton(onClick = { onShowNewSessionDialog() }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(strings.add_session),
                        modifier = Modifier.size(15.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TerminalStatusBar(terminalActivity: Terminal) {
    val colorScheme = MaterialTheme.colorScheme
    val sessionId = terminalActivity.sessionBinder?.get()?.getService()?.currentSession?.value
    val sessionMetadata = sessionId?.let { terminalActivity.sessionBinder?.get()?.getService()?.getSessionMetadata(it) }
    val displayName = sessionMetadata?.name ?: sessionId ?: "…"
    val modeText = if (sessionMetadata?.isSandbox ?: true) "sandbox: ubuntu · proot" else "shell: native"

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colorScheme.surfaceContainer)
                .border(1.dp, colorScheme.outlineVariant)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier =
                Modifier
                    .size(7.dp)
                    .background(colorScheme.secondary, RoundedCornerShape(50))
        )
        Text(
            displayName,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface,
            maxLines = 1
        )
        Text("·", color = colorScheme.onSurfaceVariant, fontSize = 11.sp)
        Text(
            "pty",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            modeText,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1
        )
    }
}

@Composable
private fun TerminalFooter(terminalActivity: Terminal) {
    val colorScheme = MaterialTheme.colorScheme
    val sessionCount = terminalActivity.sessionBinder?.get()?.getService()?.sessionList?.size ?: 0

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "❯",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = colorScheme.primary
        )
        Text(
            "ai-ready",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (sessionCount == 1) "1 session" else "$sessionCount sessions",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun TerminalView(
    isDarkMode: Boolean,
    currentTheme: ThemeHolder,
    surfaceColor: Int,
    onSurfaceColor: Int,
    terminalActivity: Terminal,
) {
    AndroidView(
        factory = { context ->
            TerminalView(context, null).apply {
                val terminalColors =
                    if (isDarkMode) {
                        currentTheme.darkTerminalColors
                    } else {
                        currentTheme.lightTerminalColors
                    }
                applyTerminalColors(
                    surfaceColor = surfaceColor,
                    onSurfaceColor = onSurfaceColor,
                    terminalColors = terminalColors,
                )

                terminalView = WeakReference(this)
                setTextSize(dpToPx(Settings.terminal_font_size.toFloat(), context))
                val client = TerminalBackEnd()

                val session =
                    if (pendingCommand != null) {
                        terminalActivity.sessionBinder?.get()!!.getService().currentSession.value = pendingCommand!!.id
                        terminalActivity.sessionBinder?.get()!!.getSession(pendingCommand!!.id)
                            ?: terminalActivity.sessionBinder
                                ?.get()!!
                                .createSession(pendingCommand!!.id, client, terminalActivity)
                                .session
                    } else {
                        terminalActivity.sessionBinder
                            ?.get()!!
                            .getSession(terminalActivity.sessionBinder?.get()!!.getService().currentSession.value)
                            ?: terminalActivity.sessionBinder
                                ?.get()!!
                                .createSession(
                                    terminalActivity.sessionBinder?.get()!!.getService().currentSession.value,
                                    client,
                                    terminalActivity,
                                )
                                .session
                    }

                session.updateTerminalSessionClient(client)
                attachSession(session)
                setTerminalViewClient(client)

                // Legacy behavior
                val fontFile = sandboxDir().child("etc/font.ttf")
                if (fontFile.exists()) {
                    setTypeface(Typeface.createFromFile(fontFile))
                } else {
                    val fontPath = Settings.terminal_font_path
                    val font =
                        if (fontPath.isNotEmpty()) {
                            FontCache.getTypeface(context, fontPath, Settings.is_terminal_font_asset)
                                ?: FontCache.getTypeface(context, DEFAULT_TERMINAL_FONT_PATH, true)
                        } else {
                            FontCache.getTypeface(context, DEFAULT_TERMINAL_FONT_PATH, true)
                        }

                    setTypeface(font)
                }

                addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                    val widthChanged = (right - left) != (oldRight - oldLeft)
                    val heightChanged = (bottom - top) != (oldBottom - oldTop)

                    if (widthChanged || heightChanged) {
                        val terminalColors =
                            if (isDarkMode) {
                                currentTheme.darkTerminalColors
                            } else {
                                currentTheme.lightTerminalColors
                            }
                        terminalView
                            .get()
                            ?.applyTerminalColors(
                                surfaceColor = surfaceColor,
                                onSurfaceColor = onSurfaceColor,
                                terminalColors = terminalColors,
                            )
                    }
                }

                post {
                    keepScreenOn = true
                    isFocusableInTouchMode = true
                    requestFocus()
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { terminalView ->
            val terminalColors =
                if (isDarkMode) {
                    currentTheme.darkTerminalColors
                } else {
                    currentTheme.lightTerminalColors
                }

            terminalView.applyTerminalColors(
                surfaceColor = surfaceColor,
                onSurfaceColor = onSurfaceColor,
                terminalColors = terminalColors,
            )
        },
    )
}

@Composable
private fun TerminalDrawer(drawerWidth: Dp, terminalActivity: Terminal, navController: NavController) {
    var showNewSessionDialogInDrawer by remember { mutableStateOf(false) }
    var showCloseSessionDialogInDrawer by remember { mutableStateOf(false) }
    var sessionToCloseInDrawer by remember { mutableStateOf<String?>(null) }
    var newSessionNameInDrawer by remember { mutableStateOf("") }
    var newSessionUseSandboxInDrawer by remember { mutableStateOf(Settings.sandbox) }

    // New Session Dialog in Drawer
    if (showNewSessionDialogInDrawer) {
        AlertDialog(
            onDismissRequest = { 
                showNewSessionDialogInDrawer = false
                newSessionNameInDrawer = ""
                newSessionUseSandboxInDrawer = Settings.sandbox
            },
            title = { Text("New Terminal Session") },
            text = {
                Column {
                    TextField(
                        value = newSessionNameInDrawer,
                        onValueChange = { newSessionNameInDrawer = it },
                        label = { Text("Session Name") },
                        placeholder = { Text("e.g., Dev Server, Git, etc.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Checkbox(
                            checked = newSessionUseSandboxInDrawer,
                            onCheckedChange = { newSessionUseSandboxInDrawer = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Use PRoot/Ubuntu Sandbox")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newSessionNameInDrawer.ifBlank { "Session ${terminalActivity.sessionBinder?.get()?.getService()?.sessionList?.size?.plus(1) ?: 1}" }
                        val sessionId = "session_${System.currentTimeMillis()}"
                        
                        terminalView.get()?.let {
                            val client = TerminalBackEnd()
                            terminalActivity.sessionBinder
                                ?.get()!!
                                .createSession(sessionId, client, terminalActivity, name, newSessionUseSandboxInDrawer)
                        }
                        
                        showNewSessionDialogInDrawer = false
                        newSessionNameInDrawer = ""
                        newSessionUseSandboxInDrawer = Settings.sandbox
                    }
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showNewSessionDialogInDrawer = false
                        newSessionNameInDrawer = ""
                        newSessionUseSandboxInDrawer = Settings.sandbox
                    }
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // Close Session Confirmation Dialog in Drawer
    if (showCloseSessionDialogInDrawer && sessionToCloseInDrawer != null) {
        AlertDialog(
            onDismissRequest = { 
                showCloseSessionDialogInDrawer = false
                sessionToCloseInDrawer = null
            },
            title = { Text("Close Session") },
            text = {
                Text("Are you sure you want to close this session? Any unsaved work may be lost.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sessionToCloseInDrawer?.let { sessionId ->
                            val service = terminalActivity.sessionBinder?.get()?.getService() ?: return@TextButton
                            
                            if (sessionId == service.currentSession.value) {
                                val index = service.sessionList.indexOf(sessionId)
                                val neighbor = service.sessionList.getOrNull(index - 1) ?: service.sessionList.getOrNull(index + 1)
                                neighbor?.let { terminalActivity.changeSession(it) }
                            }
                            terminalActivity.sessionBinder?.get()?.terminateSession(sessionId)
                            
                            if (service.sessionList.isEmpty()) {
                                terminalActivity.finish()
                                service.actionExit()
                            }
                        }
                        showCloseSessionDialogInDrawer = false
                        sessionToCloseInDrawer = null
                    }
                ) {
                    Text("Close")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCloseSessionDialogInDrawer = false
                        sessionToCloseInDrawer = null
                    }
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    ModalDrawerSheet(modifier = Modifier.width(drawerWidth)) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = stringResource(strings.sessions), style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.End) {
                    IconButton(
                        onClick = { showNewSessionDialogInDrawer = true }
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = stringResource(strings.add_session))
                    }

                    IconButton(onClick = { navController.navigate(SettingsRoutes.TerminalSettings.route) }) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = stringResource(strings.settings),
                        )
                    }
                }
            }

            val service = terminalActivity.sessionBinder?.get()?.getService()
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            NavigationDrawerItem(
                label = { Text(text = stringResource(strings.setup_title)) },
                selected = false,
                onClick = { navController.navigate("aura_setups") },
                icon = {
                    Icon(
                        painter = painterResource(drawables.widgets),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            NavigationDrawerItem(
                label = { Text(text = stringResource(strings.memory_title)) },
                selected = false,
                onClick = { navController.navigate("aura_chat_memory") },
                icon = {
                    Icon(
                        painter = painterResource(drawables.comment),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            NavigationDrawerItem(
                label = { Text(text = "Session History") },
                selected = false,
                onClick = { navController.navigate("session_history") },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                    )
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            service?.sessionList?.let {
                LazyColumn {
                    items(it) { sessionId ->
                        val isSelected = sessionId == service.currentSession.value
                        val sessionMetadata = service.getSessionMetadata(sessionId)
                        val displayName = sessionMetadata?.name ?: sessionId
                        val isSandbox = sessionMetadata?.isSandbox ?: true
                        
                        NavigationDrawerItem(
                            label = { 
                                Column {
                                    Text(text = displayName)
                                    Text(
                                        text = if (isSandbox) "PRoot/Ubuntu" else "Native Shell",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                            },
                            selected = isSelected,
                            onClick = { terminalActivity.changeSession(sessionId) },
                            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                            badge = {
                                IconButton(
                                    onClick = {
                                        if (Settings.confirm_close_session) {
                                            sessionToCloseInDrawer = sessionId
                                            showCloseSessionDialogInDrawer = true
                                        } else {
                                            if (isSelected) {
                                                val index = service.sessionList.indexOf(sessionId)
                                                val sessionBefore = service.sessionList.getOrNull(index - 1)
                                                val sessionAfter = service.sessionList.getOrNull(index + 1)
                                                val neighborSession = sessionBefore ?: sessionAfter
                                                neighborSession?.let { terminalActivity.changeSession(it) }
                                            }

                                            terminalActivity.sessionBinder?.get()?.terminateSession(sessionId)

                                            if (service.sessionList.isEmpty()) {
                                                terminalActivity.finish()
                                                service.actionExit()
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(strings.delete),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

fun Terminal.changeSession(sessionId: String) {
    val terminalView = terminalView.get() ?: return
    val binder = sessionBinder!!.get()!!

    val existingSession = binder.getSession(sessionId)
    val historySession = SessionHistory.getSession(sessionId)
    
    val client = TerminalBackEnd()
    val session = existingSession ?: binder.createSession(sessionId, client, this).session

    session.updateTerminalSessionClient(client)
    terminalView.attachSession(session)
    terminalView.setTerminalViewClient(client)

    terminalView.apply {
        post {
            keepScreenOn = true
            isFocusableInTouchMode = true
            requestFocus()
        }
    }
    virtualKeysView.get()?.apply { virtualKeysViewClient = VirtualKeysListener(terminalView.mTermSession) }

    binder.getService().currentSession.value = sessionId
}

private fun TerminalView.applyTerminalColors(onSurfaceColor: Int, surfaceColor: Int, terminalColors: Properties) {
    this.onScreenUpdated()

    mEmulator?.mColors?.reset()
    TerminalColors.COLOR_SCHEME.updateWith(terminalColors)

    mEmulator?.mColors?.mCurrentColors?.apply {
        set(TextStyle.COLOR_INDEX_FOREGROUND, onSurfaceColor)
        set(TextStyle.COLOR_INDEX_BACKGROUND, surfaceColor)
        set(TextStyle.COLOR_INDEX_CURSOR, onSurfaceColor)
    }

    invalidate()
}
