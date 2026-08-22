package com.foldspace.launcher.ui.home

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldspace.launcher.home.HomeItem
import com.foldspace.launcher.home.HomeLayout
import com.foldspace.launcher.notifications.NotificationSummary
import com.foldspace.launcher.ui.components.AppTile
import com.foldspace.launcher.ui.theme.FoldSpaceTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 簡易 — an enlarged clock and four apps.
 *
 * Everything that makes the other Spaces flexible is absent on purpose: no
 * pages, no folders, no widgets, no swiping between screens. A simplified mode
 * that still hides things behind gestures is not simplified.
 */
@Composable
fun SimpleHome(
    layout: HomeLayout,
    notifications: NotificationSummary,
    onLaunch: (HomeItem) -> Unit,
    onLongPress: (HomeItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val tokens = FoldSpaceTheme.tokens
    val items = layout.pages.firstOrNull()?.items.orEmpty().take(SIMPLE_SLOTS)

    Column(
        modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BigClock()

        val urgent = notifications.mostUrgent(limit = 1).firstOrNull()
        if (urgent != null) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = urgent.title ?: urgent.packageName.substringAfterLast('.'),
                style = MaterialTheme.typography.titleMedium,
                color = tokens.accent,
                maxLines = 2,
            )
        }

        Spacer(Modifier.height(48.dp))

        if (items.isEmpty()) {
            Text(
                text = "還沒有選擇 App",
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.textMuted,
            )
            return@Column
        }

        // Two rows of two rather than one row of four: at 96dp an icon plus a
        // readable label does not fit four across on a cover screen.
        items.chunked(2).forEach { pair ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                pair.forEach { item ->
                    val app = item.app ?: return@forEach
                    AppTile(
                        entry = app,
                        onClick = { onLaunch(item) },
                        onLongClick = { onLongPress(item) },
                        iconSize = SIMPLE_ICON_DP.dp,
                        badgeCount = notifications.countFor(app.packageName),
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun BigClock() {
    val tokens = FoldSpaceTheme.tokens
    val now by produceState(initialValue = Date()) {
        while (true) {
            value = Date()
            val calendar = Calendar.getInstance()
            delay(
                60_000L - (calendar.get(Calendar.SECOND) * 1000L +
                    calendar.get(Calendar.MILLISECOND)),
            )
        }
    }

    Box(contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = timeFormat.format(now),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 104.sp,
                    lineHeight = 108.sp,
                    fontWeight = FontWeight.Light,
                ),
                color = tokens.textPrimary,
            )
            Text(
                text = dateFormat.format(now),
                style = MaterialTheme.typography.titleMedium,
                color = tokens.textSecondary,
            )
        }
    }
}

/** §3 — 簡易 shows exactly four. */
const val SIMPLE_SLOTS = 4
private const val SIMPLE_ICON_DP = 96

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFormat = SimpleDateFormat("M 月 d 日 EEEE", Locale.getDefault())
