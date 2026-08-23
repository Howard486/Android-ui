package com.foldspace.launcher.ui.quick

import android.media.AudioManager
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.quick.QuickAction
import com.foldspace.launcher.quick.QuickActions
import com.foldspace.launcher.quick.QuickController
import com.foldspace.launcher.quick.QuickKind
import com.foldspace.launcher.ui.components.FoldCard
import com.foldspace.launcher.ui.components.TextAction
import com.foldspace.launcher.ui.theme.FoldSpaceTheme

/**
 * A quick panel that does what it can and says what it cannot.
 *
 * The iOS-style launchers put Wi-Fi, mobile data and airplane mode on a panel
 * like this. On Android none of those can be switched by an ordinary app —
 * `setWifiEnabled` has returned false without acting since API 29 — so what
 * those panels actually do when you press the button is open Settings.
 *
 * This one separates the two out loud. Torch and volume are real switches.
 * Anything else is drawn as what it is: a way to reach the system's own
 * surface, with the reason stated once at the bottom rather than left to be
 * discovered by pressing a switch that does not move.
 */
@Composable
fun QuickPanel(
    controller: QuickController,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens
    val actions = remember(controller) { controller.visibleActions() }

    Box(
        modifier
            .fillMaxSize()
            .background(tokens.overlayScrim())
            .clickable(onClick = onDismiss)
            .padding(contentPadding),
        // Top-right, where the gesture that opens it starts.
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            Modifier
                .padding(12.dp)
                .widthIn(max = PANEL_MAX_WIDTH),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "快速面板",
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textPrimary,
                )
                TextAction(text = "關閉", onClick = onDismiss)
            }

            if (QuickAction.Torch in actions) {
                TorchRow(controller)
            }

            VolumeRow(controller, "媒體音量", AudioManager.STREAM_MUSIC)
            VolumeRow(controller, "鈴聲音量", AudioManager.STREAM_RING)

            if (controller.kindOf(QuickAction.Brightness) == QuickKind.Direct) {
                BrightnessRow(controller)
            }

            val delegated = actions.filter { controller.kindOf(it) != QuickKind.Direct }
            if (delegated.isNotEmpty()) {
                FoldCard(Modifier.fillMaxWidth()) {
                    delegated.forEach { action ->
                        val kind = controller.kindOf(action)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { controller.open(action) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = action.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textPrimary,
                            )
                            Text(
                                text = QuickActions.explain(kind),
                                style = MaterialTheme.typography.labelSmall,
                                color = tokens.textMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Wi-Fi、行動網路和飛航模式沒有辦法由第三方 Launcher 切換 —— " +
                            "系統從 Android 10 起就關掉了這條路。所以這幾項是叫出系統自己的面板，" +
                            "而不是做成一個按了不會動的開關。",
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun TorchRow(controller: QuickController) {
    val tokens = FoldSpaceTheme.tokens
    var on by remember { mutableStateOf(false) }

    FoldCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = QuickAction.Torch.label,
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textPrimary,
            )
            Switch(
                checked = on,
                // The state follows what the camera actually did, not what was
                // asked for: setTorchMode fails while another app holds the
                // camera, and a switch stuck on with the light off is worse
                // than one that refuses to move.
                onCheckedChange = { wanted -> controller.setTorch(wanted)?.let { on = it } },
            )
        }
    }
}

@Composable
private fun VolumeRow(controller: QuickController, label: String, stream: Int) {
    val tokens = FoldSpaceTheme.tokens
    val initial = remember(stream) { controller.volume(stream) } ?: return
    var value by remember(stream) { mutableFloatStateOf(initial.first.toFloat()) }
    val max = initial.second.coerceAtLeast(1)

    FoldCard(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textPrimary,
        )
        Slider(
            value = value,
            onValueChange = {
                value = it
                controller.setVolume(stream, it.toInt())
            },
            valueRange = 0f..max.toFloat(),
            steps = (max - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun BrightnessRow(controller: QuickController) {
    val tokens = FoldSpaceTheme.tokens
    val initial = remember { controller.brightness() } ?: return
    var value by remember { mutableFloatStateOf(initial.toFloat()) }

    FoldCard(Modifier.fillMaxWidth()) {
        Text(
            text = QuickAction.Brightness.label,
            style = MaterialTheme.typography.bodyMedium,
            color = tokens.textPrimary,
        )
        Slider(
            value = value,
            onValueChange = {
                value = it
                controller.setBrightness(it.toInt())
            },
            valueRange = 1f..255f,
        )
    }
}

private val PANEL_MAX_WIDTH = 360.dp
