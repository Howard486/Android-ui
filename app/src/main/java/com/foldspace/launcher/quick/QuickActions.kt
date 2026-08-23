package com.foldspace.launcher.quick

/**
 * What a quick toggle can actually do on Android.
 *
 * This distinction is the whole reason the panel exists in this shape. The
 * iOS-style launchers advertise a Control Center with Wi-Fi and mobile data
 * switches; on any modern Android those switches cannot work.
 * `WifiManager.setWifiEnabled` has been deprecated since API 29 and **returns
 * false without doing anything**; mobile data and airplane mode are closed to
 * ordinary apps entirely. What those panels really do is open Settings.
 *
 * So each control declares which kind it is, and the panel draws the three
 * kinds differently. A button that quietly opens Settings while looking like a
 * switch is the substitution this project refused for Google Discover, for App
 * Pairs and for Gemini Nano.
 */
enum class QuickKind {
    /** FoldSpace changes it directly, on the spot. */
    Direct,

    /** The system's own panel opens *over* FoldSpace. Not a Settings jump. */
    SystemPanel,

    /** A full Settings screen. The platform offers nothing closer. */
    FullSettings,

    /** Not possible here — wrong API level, or no such hardware. */
    Unavailable,
}

enum class QuickAction(val label: String) {
    Torch("手電筒"),
    MediaVolume("媒體音量"),
    RingVolume("鈴聲音量"),
    Brightness("螢幕亮度"),
    Internet("網路"),
    Wifi("Wi-Fi"),
    Bluetooth("藍牙"),
    Rotation("自動旋轉"),
}

object QuickActions {

    /** `Settings.Panel.ACTION_INTERNET_CONNECTIVITY` arrived in API 29. */
    const val INTERNET_PANEL_SDK = 29

    /**
     * What [action] can do, given this device and what the user has granted.
     *
     * Pure so the table can be checked without a device — which matters,
     * because getting one of these wrong means a control that looks live and
     * does nothing.
     */
    fun kindOf(
        action: QuickAction,
        sdkInt: Int,
        hasTorch: Boolean,
        canWriteSettings: Boolean,
    ): QuickKind = when (action) {
        QuickAction.Torch ->
            if (hasTorch) QuickKind.Direct else QuickKind.Unavailable

        // AudioManager has always let an ordinary app move the streams it can
        // already hear. These two are the only unqualified wins on the panel.
        QuickAction.MediaVolume, QuickAction.RingVolume -> QuickKind.Direct

        // Both need WRITE_SETTINGS, which is a special permission the user
        // grants on a system screen. Until then the system panel is honest and
        // a dead slider is not.
        QuickAction.Brightness, QuickAction.Rotation ->
            if (canWriteSettings) QuickKind.Direct else QuickKind.FullSettings

        QuickAction.Internet ->
            if (sdkInt >= INTERNET_PANEL_SDK) QuickKind.SystemPanel else QuickKind.Unavailable

        QuickAction.Wifi -> QuickKind.SystemPanel

        // Bluetooth has no Settings.Panel action at all, so this is the
        // closest the platform gets.
        QuickAction.Bluetooth -> QuickKind.FullSettings
    }

    /** The controls worth drawing, in the order they are drawn. */
    fun visible(sdkInt: Int, hasTorch: Boolean, canWriteSettings: Boolean): List<QuickAction> =
        QuickAction.entries.filter {
            kindOf(it, sdkInt, hasTorch, canWriteSettings) != QuickKind.Unavailable
        }

    /** One line explaining a kind, shown next to the controls rather than hidden. */
    fun explain(kind: QuickKind): String = when (kind) {
        QuickKind.Direct -> "FoldSpace 直接切換"
        QuickKind.SystemPanel -> "開啟系統面板（疊在 FoldSpace 上）"
        QuickKind.FullSettings -> "開啟系統設定頁"
        QuickKind.Unavailable -> "這台裝置不支援"
    }
}
