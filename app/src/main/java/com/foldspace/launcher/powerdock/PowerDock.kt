package com.foldspace.launcher.powerdock

import com.foldspace.launcher.context.ContextSnapshot
import com.foldspace.launcher.context.TimeBucket
import com.foldspace.launcher.ui.layout.LayoutMode

/** §11.1 — the three dock presets. */
enum class PowerDockMode(val displayName: String) {
    Desk("Desk"),
    Bedside("Bedside"),
    Cyber("Cyber"),
    /** Folded and charging: too small for a dashboard, so a compact readout. */
    CompactCharge("Charge"),
}

data class PowerDockState(
    val active: Boolean = false,
    val mode: PowerDockMode = PowerDockMode.Desk,
    val batteryPercent: Int = 0,
    val chargeEtaMillis: Long? = null,
) {
    /** §11.3 — the ETA is optional by design (§22: OEM support varies). */
    val etaText: String?
        get() = chargeEtaMillis?.let { millis ->
            val minutes = (millis / 60_000L).toInt()
            when {
                minutes <= 0 -> null
                minutes < 60 -> "約 $minutes 分鐘充滿"
                else -> "約 ${minutes / 60} 小時 ${minutes % 60} 分充滿"
            }
        }
}

/**
 * §11.2 Power Dock trigger, as a pure function of the snapshot.
 *
 * Keeping it pure means the "unplug restores the previous Space" gate (§21.1)
 * is decided in one place, and the caller only has to remember what the Space
 * was before.
 */
object PowerDockResolver {

    fun resolve(snapshot: ContextSnapshot, chargeEtaMillis: Long?): PowerDockState {
        if (!snapshot.charging) return PowerDockState(active = false)

        val mode = when {
            // Night rule first: a phone charging at 2am is a bedside clock, not
            // a desk dashboard, whatever its posture says.
            snapshot.timeBucket == TimeBucket.Night -> PowerDockMode.Bedside
            snapshot.layoutMode == LayoutMode.Compact -> PowerDockMode.CompactCharge
            snapshot.layoutMode == LayoutMode.Tabletop -> PowerDockMode.Cyber
            else -> PowerDockMode.Desk
        }

        return PowerDockState(
            active = true,
            mode = mode,
            batteryPercent = snapshot.batteryPercent,
            // §11.4 / §12.3 — no HUD animation data while saving power.
            chargeEtaMillis = chargeEtaMillis.takeUnless { snapshot.powerSave },
        )
    }
}
