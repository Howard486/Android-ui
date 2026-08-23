package com.foldspace.launcher.ui.icons

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar

/**
 * Today's day of the month, for icon packs that supply dynamic calendar art.
 *
 * A composition local rather than a value each icon reads for itself: a page
 * of icons is a few dozen composables and a drawer is several hundred, and one
 * `BroadcastReceiver` between them is the difference between a launcher and a
 * leak.
 *
 * Defaults to 1 so an icon rendered outside the provider still resolves to a
 * real drawable rather than to nothing.
 */
val LocalDayOfMonth: ProvidableCompositionLocal<Int> = staticCompositionLocalOf { 1 }

/**
 * The current day, kept up to date while the launcher is on screen.
 *
 * Polling would be the obvious way and the wrong one — §12.1 has no room for a
 * timer whose only job is to notice midnight. The platform already broadcasts
 * it. A launcher is genuinely the app on screen when the date rolls over, so
 * this is not a theoretical case: without it the calendar icon would sit on
 * yesterday until something evicted it from the bitmap cache.
 */
@Composable
fun rememberDayOfMonth(): Int {
    val context = LocalContext.current
    var day by remember { mutableIntStateOf(today()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                day = today()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // Not exported: these are system broadcasts, and saying so is required
        // from API 34 anyway.
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    return day
}

private fun today(): Int = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
