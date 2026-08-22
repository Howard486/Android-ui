package com.foldspace.launcher.feed

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Google Discover, the "-1 screen".
 *
 * **This provider detects and declines. It does not render the overlay.**
 *
 * What a third-party launcher would have to do is bind to the Google app's
 * launcher-overlay service over Google's private `ILauncherOverlay` AIDL and
 * host the returned surface in its own window. The public evidence is that
 * this is not dependable for an app like ours:
 *
 *  - launchers that ship it generally need a separate bridge app (Lawnchair's
 *    Lawnfeed, Nova's companion) rather than binding directly;
 *  - Google gates the current feed presentation on the caller being the Pixel
 *    Launcher package on non-Pixel hardware;
 *  - the launchers that do implement it carry long-running "feed stopped
 *    working" reports that vary by device and Google app version.
 *
 * Shipping a provider that pretends to connect and silently renders nothing
 * would be worse than one that says plainly that it cannot. So this reports
 * whether the overlay service is even present, and the composed page falls
 * through to the next provider.
 *
 * Whether the real integration is possible on a Galaxy Z Fold is a §22
 * on-device question, not something that can be settled from code.
 */
class GoogleOverlayFeedProvider(private val context: Context) : FeedProvider {

    override val name: String = "Google Discover"

    /**
     * Present means "the Google app exposes the overlay service", not "we can
     * use it" — hence [load] declining even when this is true.
     */
    fun overlayServicePresent(): Boolean {
        val intent = Intent(OVERLAY_ACTION).setPackage(GOOGLE_APP_PACKAGE)
        val matches = runCatching {
            context.packageManager.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList())
        return matches.isNotEmpty()
    }

    override fun isAvailable(): Boolean = false

    override suspend fun load(): FeedState = FeedState.Unavailable(
        reason = if (overlayServicePresent()) {
            "偵測到 Google 的 overlay 服務，但第三方 Launcher 無法直接使用（Google 以套件名限制）。已改用 Google 新聞。"
        } else {
            "此裝置未提供 Google Discover overlay。已改用 Google 新聞。"
        },
    )

    private companion object {
        const val GOOGLE_APP_PACKAGE = "com.google.android.googlequicksearchbox"
        const val OVERLAY_ACTION = "com.google.android.apps.gsa.smartspace.SmartspaceUpdate"
    }
}
