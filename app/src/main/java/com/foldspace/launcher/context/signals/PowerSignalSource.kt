package com.foldspace.launcher.context.signals

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.foldspace.launcher.context.ContextEvent

/**
 * §11.2 / §12.1 — power state as pure broadcasts. No battery polling: the
 * system already tells us when it matters, and a launcher that polls the
 * battery is exactly what §12 exists to prevent.
 */
class PowerSignalSource(
    private val context: Context,
    private val onEvent: (ContextEvent) -> Unit,
) {

    private var receiver: BroadcastReceiver? = null

    fun start() {
        if (receiver != null) return

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            // Sticky-adjacent: only fires on whole-percent changes, so it is
            // far cheaper than ACTION_BATTERY_CHANGED.
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_BATTERY_OKAY)
        }

        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_POWER_CONNECTED ->
                        onEvent(ContextEvent.PowerChanged(true, batteryPercent()))

                    Intent.ACTION_POWER_DISCONNECTED ->
                        onEvent(ContextEvent.PowerChanged(false, batteryPercent()))

                    PowerManager.ACTION_POWER_SAVE_MODE_CHANGED ->
                        onEvent(ContextEvent.PowerSaveChanged(isPowerSaveMode()))

                    Intent.ACTION_BATTERY_LOW, Intent.ACTION_BATTERY_OKAY ->
                        onEvent(ContextEvent.PowerChanged(isCharging(), batteryPercent()))
                }
            }
        }
        receiver = r
        ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        // Seed the engine with where things already stand.
        onEvent(ContextEvent.PowerChanged(isCharging(), batteryPercent()))
        onEvent(ContextEvent.PowerSaveChanged(isPowerSaveMode()))
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }

    fun batteryPercent(): Int =
        batteryManager()?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

    fun isCharging(): Boolean = batteryManager()?.isCharging ?: false

    /**
     * §11.3 — charge ETA. Deliberately optional: §22 says OEM data
     * availability varies and the UI must not promise a figure it cannot get.
     */
    fun chargeTimeRemainingMillis(): Long? {
        val bm = batteryManager() ?: return null
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return null
        return bm.computeChargeTimeRemaining().takeIf { it > 0 }
    }

    private fun isPowerSaveMode(): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode ?: false

    private fun batteryManager(): BatteryManager? =
        context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
}

/**
 * Declared in the manifest but disabled: ACTION_POWER_CONNECTED cannot be
 * delivered to a manifest receiver since API 26, so the real registration is
 * runtime (above). Kept as the hook for boot-time work if that lands later.
 */
class PowerConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
