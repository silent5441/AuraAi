package com.rk.components.compose.appbars

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DotRed = Color(0xFFFF5F6E)
private val DotAmber = Color(0xFFFFC857)
private val DotGreen = Color(0xFF43E39B)

/** Terminal window traffic lights (macOS-style). */
@Composable
fun TrafficDots(modifier: Modifier = Modifier, dotSize: Dp = 7.dp) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(dotSize).clip(CircleShape).background(DotRed.copy(alpha = 0.9f)))
        Box(Modifier.size(dotSize).clip(CircleShape).background(DotAmber.copy(alpha = 0.9f)))
        Box(Modifier.size(dotSize).clip(CircleShape).background(DotGreen.copy(alpha = 0.9f)))
    }
}

/**
 * Terminal-style chrome header: traffic lights + uppercase title + divider,
 * matching the AuraAi terminal window look. Handles the status bar inset.
 */
@Composable
fun TerminalHeader(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrafficDots()
            Spacer(Modifier.size(10.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false),
            )
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f),
            thickness = 1.dp,
        )
    }
}