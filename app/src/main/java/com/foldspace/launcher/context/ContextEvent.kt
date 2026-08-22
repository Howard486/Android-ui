package com.foldspace.launcher.context

import com.foldspace.launcher.ui.layout.LayoutMode

/**
 * §7 — every input to the Context Engine is one of these. There is no polling
 * loop anywhere; each event originates from a system callback, a broadcast, or
 * the launcher itself coming to the foreground.
 */
sealed interface ContextEvent {
    data class FoldChanged(val layoutMode: LayoutMode) : ContextEvent

    data class PowerChanged(val charging: Boolean, val batteryPercent: Int) : ContextEvent

    data class BluetoothChanged(val bluetoothClass: BluetoothClass) : ContextEvent

    data class PowerSaveChanged(val powerSave: Boolean) : ContextEvent

    data class CalendarChanged(val category: String?) : ContextEvent

    data class UsageRefreshed(val scores: Map<String, Float>) : ContextEvent

    /** Launcher resumed/paused. Gates Nano eligibility (§8, foreground-only). */
    data class LauncherForeground(val foreground: Boolean, val hourOfDay: Int, val isWeekday: Boolean) :
        ContextEvent

    /** Time bucket rolled over while the launcher was visible. */
    data class TimeChanged(val hourOfDay: Int, val isWeekday: Boolean) : ContextEvent
}

/**
 * Folds events into the current snapshot. Pure function — no Android types, so
 * it is directly unit-testable (§21.2 asks for fingerprint behaviour to be
 * provable, and this is where that lives).
 */
object ContextReducer {

    fun reduce(current: ContextSnapshot, event: ContextEvent, now: Long): ContextSnapshot =
        when (event) {
            is ContextEvent.FoldChanged ->
                current.copy(layoutMode = event.layoutMode, timestamp = now)

            is ContextEvent.PowerChanged -> current.copy(
                charging = event.charging,
                batteryPercent = event.batteryPercent.coerceIn(0, 100),
                timestamp = now,
            )

            is ContextEvent.BluetoothChanged ->
                current.copy(bluetoothClass = event.bluetoothClass, timestamp = now)

            is ContextEvent.PowerSaveChanged ->
                current.copy(powerSave = event.powerSave, timestamp = now)

            is ContextEvent.CalendarChanged ->
                current.copy(calendarCategory = event.category, timestamp = now)

            is ContextEvent.UsageRefreshed ->
                current.copy(usageScores = event.scores, timestamp = now)

            is ContextEvent.LauncherForeground -> current.copy(
                launcherForeground = event.foreground,
                timeBucket = TimeBucket.of(event.hourOfDay),
                isWeekday = event.isWeekday,
                timestamp = now,
            )

            is ContextEvent.TimeChanged -> current.copy(
                timeBucket = TimeBucket.of(event.hourOfDay),
                isWeekday = event.isWeekday,
                timestamp = now,
            )
        }
}
