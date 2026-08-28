package com.rk.settings.agent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rk.activities.settings.SettingsActivity
import com.rk.activities.settings.SettingsRoutes
import com.rk.activities.settings.settingsNavController
import com.rk.agent.AgentBridge
import com.rk.components.InfoBlock
import com.rk.components.compose.preferences.base.PreferenceGroup
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceTemplate
import com.rk.components.compose.preferences.switch.PreferenceSwitch
import com.rk.exec.TerminalCommand
import com.rk.exec.isTerminalInstalled
import com.rk.exec.launchTerminal
import com.rk.resources.drawables
import com.rk.resources.getFilledString
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.settings.Settings
import com.rk.theme.greenStatus

@Composable
fun AgentScreen(activity: SettingsActivity) {
    var bridgeEnabled by remember { mutableStateOf(Settings.agent_bridge_enabled) }
    var bridgeRunning by remember { mutableStateOf(AgentBridge.isRunning) }
    var portText by remember { mutableStateOf(Settings.agent_bridge_port.toString()) }

    LaunchedEffect(bridgeEnabled) { bridgeRunning = AgentBridge.isRunning }

    PreferenceLayout(label = stringResource(strings.agent), backArrowVisible = true) {
        if (!isTerminalInstalled()) {
            InfoBlock(
                icon = { Icon(imageVector = Icons.Outlined.Warning, contentDescription = null) },
                text =
                    stringResource(strings.agent_needs_terminal) +
                        "\n" +
                        stringResource(strings.agent_needs_terminal_desc),
                warning = true,
            )
        } else {
            PreferenceGroup {
                PreferenceSwitch(
                    checked = bridgeEnabled,
                    onCheckedChange = { enabled ->
                        Settings.agent_bridge_enabled = enabled
                        bridgeEnabled = enabled
                        if (enabled) {
                            AgentBridge.start(activity.applicationContext)
                        } else {
                            AgentBridge.stop()
                        }
                        bridgeRunning = AgentBridge.isRunning
                    },
                    label = stringResource(strings.agent_bridge),
                    description = stringResource(strings.agent_bridge_desc),
                )
            }

            PreferenceGroup {
                Text(
                    text =
                        if (bridgeRunning) {
                            strings.agent_status_running.getFilledString(Settings.agent_bridge_port)
                        } else {
                            strings.agent_status_stopped.getString()
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (bridgeRunning) {
                            MaterialTheme.colorScheme.greenStatus
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }

            PreferenceGroup {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(strings.agent_port),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(strings.agent_port_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        OutlinedTextField(
                            value = portText,
                            onValueChange = { portText = it.filter(Char::isDigit).take(5) },
                            label = { Text(stringResource(strings.agent_port)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        TextButton(
                            enabled = portText.isNotBlank(),
                            onClick = {
                                runCatching {
                                    Settings.agent_bridge_port = portText.toInt()
                                    AgentBridge.restart(activity.applicationContext)
                                }
                                bridgeRunning = AgentBridge.isRunning
                            },
                        ) {
                            Text(stringResource(strings.restart))
                        }
                    }
                }
            }

            PreferenceGroup {
                PreferenceTemplate(
                    modifier =
                        Modifier.clickable { settingsNavController.get()?.navigate(SettingsRoutes.Setups.route) },
                    title = { Text(stringResource(strings.setup_title)) },
                    description = {
                        Text(
                            stringResource(strings.setup_desc),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    startWidget = {
                        Icon(
                            painter = painterResource(drawables.widgets),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                PreferenceTemplate(
                    modifier =
                        Modifier.clickable { settingsNavController.get()?.navigate(SettingsRoutes.ChatMemory.route) },
                    title = { Text(stringResource(strings.memory_title)) },
                    description = {
                        Text(
                            stringResource(strings.memory_desc),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    startWidget = {
                        Icon(
                            painter = painterResource(drawables.comment),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }

            PreferenceGroup {
                PreferenceTemplate(
                    modifier =
                        Modifier.clickable {
                            launchTerminal(
                                activity,
                                TerminalCommand(
                                    sandbox = true,
                                    exe = "agent-setup",
                                    id = "agent-setup",
                                    env = arrayOf("XED_BRIDGE_PORT=${Settings.agent_bridge_port}"),
                                ),
                            )
                        },
                    title = { Text(stringResource(strings.agent_setup)) },
                    description = { Text(stringResource(strings.agent_setup_desc)) },
                    startWidget = {
                        Icon(
                            painter = painterResource(drawables.bolt),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
                PreferenceTemplate(
                    modifier =
                        Modifier.clickable {
                            launchTerminal(
                                activity,
                                TerminalCommand(
                                    sandbox = true,
                                    exe = "opencode",
                                    id = "opencode",
                                    workingDir = "/home",
                                    env = arrayOf("XED_BRIDGE_PORT=${Settings.agent_bridge_port}"),
                                ),
                            )
                        },
                    title = { Text(stringResource(strings.agent_open)) },
                    description = { Text(stringResource(strings.agent_open_desc)) },
                    startWidget = {
                        Icon(
                            painter = painterResource(drawables.bolt),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                )
            }

            PreferenceGroup {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(strings.agent_tools),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(strings.agent_tools_desc),
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            PreferenceGroup {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(strings.agent_howto),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(strings.agent_howto_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
