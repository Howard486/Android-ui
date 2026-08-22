package com.foldspace.launcher.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foldspace.launcher.notifications.NotificationSummary
import com.foldspace.launcher.spaces.CardId
import com.foldspace.launcher.spaces.NotificationTier
import com.foldspace.launcher.ui.components.CardTitle
import com.foldspace.launcher.ui.components.FoldCard
import com.foldspace.launcher.ui.components.LevelBar
import com.foldspace.launcher.ui.theme.FoldSpaceTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * §13.1 Native Cards. Every card is a pure composable over state that already
 * exists — none of them starts its own data source, because a card that polls
 * would violate §12.1 the moment two of them are on screen at once.
 */
@Composable
fun NativeCard(
    id: CardId,
    notifications: NotificationSummary,
    batteryPercent: Int,
    charging: Boolean,
    modifier: Modifier = Modifier,
) {
    when (id) {
        CardId.Clock -> ClockCard(modifier)
        CardId.Battery -> BatteryCard(batteryPercent, charging, modifier)
        CardId.Notifications -> NotificationDigestCard(notifications, modifier)
        CardId.Weather -> PlaceholderCard("天氣", "接上天氣來源後顯示", modifier)
        CardId.Calendar -> PlaceholderCard("行事曆", "授予行事曆權限後顯示下一個行程", modifier)
        CardId.Tasks -> PlaceholderCard("待辦", "V0.5 接入工作清單", modifier)
        CardId.NowPlaying -> PlaceholderCard("正在播放", "沒有正在播放的媒體", modifier)
        CardId.Timer -> PlaceholderCard("計時器", "尚未設定計時器", modifier)
    }
}

/**
 * The clock ticks once a minute, aligned to the next minute boundary rather
 * than on a fixed 60s loop — a loop started at :30 would redraw at :30 every
 * minute and show the wrong minute for half of each one.
 */
@Composable
fun ClockCard(modifier: Modifier = Modifier) {
    val tokens = FoldSpaceTheme.tokens
    val now by produceState(initialValue = Date()) {
        while (true) {
            value = Date()
            val calendar = Calendar.getInstance()
            val millisToNextMinute =
                60_000L - (calendar.get(Calendar.SECOND) * 1000L + calendar.get(Calendar.MILLISECOND))
            delay(millisToNextMinute)
        }
    }

    val time = remember24HourFormat().format(now)
    val date = remember(now) { dateFormat.format(now) }

    FoldCard(modifier) {
        Text(
            text = time,
            style = MaterialTheme.typography.displayMedium,
            color = tokens.textPrimary,
        )
        Text(
            text = date,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textSecondary,
        )
    }
}

@Composable
fun BatteryCard(percent: Int, charging: Boolean, modifier: Modifier = Modifier) {
    val tokens = FoldSpaceTheme.tokens
    FoldCard(modifier) {
        CardTitle("電量")
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (percent in 0..100) "$percent%" else "—",
                style = MaterialTheme.typography.headlineSmall,
                color = tokens.textPrimary,
            )
            Spacer(Modifier.width(8.dp))
            if (charging) {
                Text(
                    text = "充電中",
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.accent,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        LevelBar(
            fraction = percent.coerceIn(0, 100) / 100f,
            color = if (charging) tokens.accent else tokens.textSecondary,
        )
    }
}

/** §10.1 "Fold 展開 Summary" — the digest, grouped by tier. */
@Composable
fun NotificationDigestCard(summary: NotificationSummary, modifier: Modifier = Modifier) {
    val tokens = FoldSpaceTheme.tokens
    FoldCard(modifier) {
        CardTitle("通知摘要")
        Spacer(Modifier.height(10.dp))

        if (!summary.listenerConnected) {
            Text(
                text = "尚未授予通知存取權",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted,
            )
            return@FoldCard
        }

        if (summary.items.isEmpty()) {
            Text(
                text = "目前沒有通知",
                style = MaterialTheme.typography.bodySmall,
                color = tokens.textMuted,
            )
            return@FoldCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NotificationTier.entries.forEach { tier ->
                val count = summary.items.count { it.tier == tier }
                if (count == 0) return@forEach
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = tier.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (tier == NotificationTier.Now) tokens.accent
                        else tokens.textSecondary,
                    )
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaceholderCard(title: String, message: String, modifier: Modifier = Modifier) {
    val tokens = FoldSpaceTheme.tokens
    FoldCard(modifier) {
        CardTitle(title)
        Spacer(Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = tokens.textMuted,
        )
    }
}

@Composable
private fun remember24HourFormat(): SimpleDateFormat {
    val is24Hour = android.text.format.DateFormat.is24HourFormat(
        androidx.compose.ui.platform.LocalContext.current,
    )
    return remember(is24Hour) {
        SimpleDateFormat(if (is24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
    }
}

private val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
