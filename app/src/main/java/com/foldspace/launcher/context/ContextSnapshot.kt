package com.foldspace.launcher.context

import com.foldspace.launcher.ui.layout.LayoutMode

/** §7 — coarse time buckets. Deliberately coarse: minute precision would churn
 *  the fingerprint every minute and defeat the cache. */
enum class TimeBucket {
    EarlyMorning,   // 05–08
    Morning,        // 08–12
    Afternoon,      // 12–18
    Evening,        // 18–22
    Night,          // 22–05
    ;

    companion object {
        fun of(hourOfDay: Int): TimeBucket = when (hourOfDay) {
            in 5..7 -> EarlyMorning
            in 8..11 -> Morning
            in 12..17 -> Afternoon
            in 18..21 -> Evening
            else -> Night
        }
    }
}

/** Coarse Bluetooth class — enough to tell "headphones" from "car" without
 *  storing device identity (§16.1: only device class is retained). */
enum class BluetoothClass {
    None, Audio, Car, Wearable, Other,
}

/** §18 ContextSnapshot. Immutable; produced by [ContextReducer] from events. */
data class ContextSnapshot(
    val timestamp: Long,
    val layoutMode: LayoutMode,
    val timeBucket: TimeBucket,
    val isWeekday: Boolean,
    val charging: Boolean,
    val batteryPercent: Int,
    val bluetoothClass: BluetoothClass,
    val calendarCategory: String?,
    /** package -> normalised recent-usage score in 0..1 (§7, UsageStats). */
    val usageScores: Map<String, Float>,
    val powerSave: Boolean,
    val launcherForeground: Boolean,
) {
    companion object {
        fun initial(): ContextSnapshot = ContextSnapshot(
            timestamp = 0L,
            layoutMode = LayoutMode.Compact,
            timeBucket = TimeBucket.Morning,
            isWeekday = true,
            charging = false,
            batteryPercent = 100,
            bluetoothClass = BluetoothClass.None,
            calendarCategory = null,
            usageScores = emptyMap(),
            powerSave = false,
            launcherForeground = false,
        )
    }
}

/**
 * §7.3 Context Fingerprint.
 *
 * The whole point is that this must be *stable* across the noise a launcher
 * sees constantly — battery ticking down a percent, usage scores drifting,
 * a new timestamp. Only the fields that could change the decision go in, and
 * the continuous ones are bucketed first. If the fingerprint is unchanged we
 * do not re-run rules and never call Nano (§12.4).
 */
@JvmInline
value class ContextFingerprint(val value: String)

fun ContextSnapshot.fingerprint(): ContextFingerprint = ContextFingerprint(
    buildString {
        append(layoutMode.name).append('|')
        append(timeBucket.name).append('|')
        append(if (isWeekday) "wd" else "we").append('|')
        append(if (charging) "chg" else "bat").append('|')
        // 20% buckets: a percent tick must not invalidate the cache.
        append(batteryPercent / 20).append('|')
        append(bluetoothClass.name).append('|')
        append(calendarCategory ?: "-").append('|')
        append(if (powerSave) "ps" else "np").append('|')
        // Only the top apps matter, and only their identity, not their scores.
        append(
            usageScores.entries
                .sortedByDescending { it.value }
                .take(TOP_USAGE_APPS_IN_FINGERPRINT)
                .joinToString(",") { it.key },
        )
    },
)

private const val TOP_USAGE_APPS_IN_FINGERPRINT = 3
