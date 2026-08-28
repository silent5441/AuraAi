package com.rk.terminal.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rk.components.InfoBlock
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.exec.isTerminalInstalled
import com.rk.resources.strings
import com.rk.theme.greenStatus
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(activity: android.app.Activity) {
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf<Set<String>?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshTick) {
        if (isTerminalInstalled()) {
            installed = null
            installed = runCatching { SetupRecipes.installedIds() }.getOrDefault(emptySet())
        }
    }

    PreferenceLayout(
        label = stringResource(strings.setup_title),
        backArrowVisible = true,
        actions = {
            IconButton(onClick = { refreshTick++ }) {
                Icon(
                    painter = painterResource(com.rk.resources.drawables.refresh),
                    contentDescription = stringResource(strings.setup_refresh),
                )
            }
        },
    ) {
        if (!isTerminalInstalled()) {
            InfoBlock(
                icon = { Icon(painterResource(com.rk.resources.drawables.info), null) },
                text = stringResource(strings.agent_needs_terminal) + "\n" + stringResource(strings.agent_needs_terminal_desc),
                warning = true,
            )
            return@PreferenceLayout
        }

        Text(
            text = stringResource(strings.setup_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SetupRecipes.all.forEach { recipe ->
                val statusKnown = installed != null
                val isInstalled = installed?.contains(recipe.id) == true

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier =
                        Modifier.fillMaxWidth().clickable {
                            SetupRecipes.launch(activity, recipe.id)
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier.size(40.dp)
                                    .clip(CircleShape)
                                    .background(recipe.tint.copy(alpha = 0.15f))
                                    .border(1.dp, recipe.tint.copy(alpha = 0.4f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(recipe.iconRes),
                                contentDescription = null,
                                tint = recipe.tint,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = recipe.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = recipe.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        StatusChip(known = statusKnown, installed = isInstalled)
                    }
                }
            }

            Text(
                text = stringResource(strings.setup_run_hint),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            )
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun StatusChip(known: Boolean, installed: Boolean) {
    val colorScheme = MaterialTheme.colorScheme
    val (label, tint) =
        when {
            !known -> stringResource(strings.setup_checking) to colorScheme.onSurfaceVariant
            installed -> stringResource(strings.setup_installed) to colorScheme.greenStatus
            else -> stringResource(strings.setup_not_installed) to colorScheme.primary
        }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        color = tint,
        modifier =
            Modifier.clip(RoundedCornerShape(50))
                .background(tint.copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
