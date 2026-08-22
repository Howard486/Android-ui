package com.foldspace.launcher.ui.powerdock

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.core.launcher.AppEntry
import com.foldspace.launcher.notifications.NotificationSummary
import com.foldspace.launcher.powerdock.PowerDockMode
import com.foldspace.launcher.powerdock.PowerDockState
import com.foldspace.launcher.ui.cards.ClockCard
import com.foldspace.launcher.ui.cards.NotificationDigestCard
import com.foldspace.launcher.ui.components.AppTile
import com.foldspace.launcher.ui.components.FoldCard
import com.foldspace.launcher.ui.components.LevelBar
import com.foldspace.launcher.ui.theme.FoldSpaceTheme
import com.foldspace.launcher.ui.theme.MotionLevel

/**
 * §11 Power Dock. Four presets over one layout skeleton: what changes between
 * them is density and brightness, not structure, so unplugging and replugging
 * never rearranges the screen under the user.
 */
@Composable
fun PowerDockScreen(
    dock: PowerDockState,
    notifications: NotificationSummary,
    shortcuts: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens
    val bedside = dock.mode == PowerDockMode.Bedside

    Column(
        modifier
            .fillMaxSize()
            .background(tokens.scrim)
            .padding(contentPadding)
            .padding(horizontal = 20.dp)
            // §11.1 — Bedside is deliberately dimmed at the composition level
            // rather than by changing screen brightness, which is the system's
            // to control, not a launcher's.
            .alpha(if (bedside) 0.72f else 1f),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Spacer(Modifier.height(24.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = dock.mode.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.textMuted,
                )
                Text(
                    text = "設定",
                    style = MaterialTheme.typography.labelSmall,
                    color = tokens.accent,
                    modifier = Modifier
                        .clickable(onClick = onOpenSettings)
                        .padding(6.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            ClockCard(Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            ChargeCard(dock)

            if (dock.mode != PowerDockMode.CompactCharge && !bedside) {
                Spacer(Modifier.height(12.dp))
                NotificationDigestCard(notifications, Modifier.fillMaxWidth())
            }
        }

        Column {
            if (shortcuts.isNotEmpty() && dock.mode != PowerDockMode.Bedside) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    shortcuts.take(SHORTCUT_COUNT).forEach { entry ->
                        AppTile(
                            entry = entry,
                            onClick = { onLaunch(entry) },
                            onLongClick = {},
                            modifier = Modifier.weight(1f),
                            iconSize = 44.dp,
                            showLabel = false,
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ChargeCard(dock: PowerDockState) {
    val tokens = FoldSpaceTheme.tokens
    FoldCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${dock.batteryPercent.coerceIn(0, 100)}%",
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.textPrimary,
            )
            if (dock.mode == PowerDockMode.Cyber) {
                EnergyRing(percent = dock.batteryPercent)
            }
        }

        Spacer(Modifier.height(10.dp))
        LevelBar(
            fraction = dock.batteryPercent.coerceIn(0, 100) / 100f,
            color = tokens.accent,
        )

        // §11.3 / §22 — only rendered where the platform actually gave us a
        // figure. No estimate is invented to fill the gap.
        dock.etaText?.let { eta ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = eta,
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textSecondary,
            )
        }
    }
}

/**
 * §11.1 Cyber mode's energy ring.
 *
 * §11.4 caps the cost: a slow single-value rotation, and nothing at all when
 * motion is off. No particles, no shader, no permanent high-FPS repaint.
 */
@Composable
private fun EnergyRing(percent: Int) {
    val tokens = FoldSpaceTheme.tokens
    val motion = FoldSpaceTheme.motion

    val sweep = (percent.coerceIn(0, 100) / 100f) * 360f
    val rotation = if (motion == MotionLevel.None) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "energy-ring")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                // 8s per revolution: visible, but nowhere near a per-frame cost.
                animation = tween(durationMillis = 8_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "energy-ring-rotation",
        )
        animated
    }

    androidx.compose.foundation.Canvas(Modifier.size(44.dp)) {
        val stroke = Stroke(width = 4.dp.toPx())
        val inset = stroke.width / 2
        drawArc(
            color = tokens.outline,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke.width, size.height - stroke.width),
            style = stroke,
        )
        drawArc(
            color = tokens.accent,
            startAngle = rotation - 90f,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke.width, size.height - stroke.width),
            style = stroke,
        )
    }
}

private const val SHORTCUT_COUNT = 5
