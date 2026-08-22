package com.foldspace.launcher.home

import android.content.Context
import android.content.pm.ApplicationInfo
import com.foldspace.launcher.core.launcher.AppEntry

/** The folders one-tap organise can produce. */
enum class AppCategory(val key: String, val displayName: String) {
    Communication("communication", "通訊"),
    Social("social", "社群"),
    Productivity("productivity", "生產力"),
    Finance("finance", "金融"),
    Shopping("shopping", "購物"),
    Media("media", "影音"),
    Photo("photo", "相片"),
    Navigation("navigation", "地圖"),
    Game("game", "遊戲"),
    News("news", "新聞"),
    Tools("tools", "工具"),
    System("system", "系統"),
    Other("other", "其他"),
    ;

    companion object {
        fun fromKey(key: String?): AppCategory =
            entries.firstOrNull { it.key == key } ?: Other
    }
}

/**
 * Works out which folder an app belongs in.
 *
 * There is no offline source that covers everything. `ApplicationInfo.category`
 * is the only official signal and a large share of apps never set it; the Play
 * Store's own categories would have to be scraped, which is not on. So this is
 * a ladder, cheapest and most reliable first, and it says so when it does not
 * know rather than guessing.
 */
class AppCategorizer(private val context: Context) {

    /**
     * A curated map for the apps a Taiwanese user is most likely to have,
     * because those are exactly the ones that tend to leave
     * `ApplicationInfo.category` unset. Deliberately small and hand-checked —
     * a large auto-generated list would be wrong in ways nobody notices.
     */
    private val knownPackages: Map<String, AppCategory> = mapOf(
        // Communication
        "jp.naver.line.android" to AppCategory.Communication,
        "com.whatsapp" to AppCategory.Communication,
        "org.telegram.messenger" to AppCategory.Communication,
        "com.facebook.orca" to AppCategory.Communication,
        "com.microsoft.teams" to AppCategory.Communication,
        "com.google.android.apps.messaging" to AppCategory.Communication,
        "com.android.dialer" to AppCategory.Communication,
        "com.samsung.android.messaging" to AppCategory.Communication,
        "com.discord" to AppCategory.Communication,
        "us.zoom.videomeetings" to AppCategory.Communication,

        // Social
        "com.facebook.katana" to AppCategory.Social,
        "com.instagram.android" to AppCategory.Social,
        "com.twitter.android" to AppCategory.Social,
        "com.zhiliaoapp.musically" to AppCategory.Social,
        "com.linkedin.android" to AppCategory.Social,
        "com.reddit.frontpage" to AppCategory.Social,
        "com.dcard.app" to AppCategory.Social,

        // Productivity
        "com.microsoft.office.outlook" to AppCategory.Productivity,
        "com.google.android.gm" to AppCategory.Productivity,
        "com.google.android.calendar" to AppCategory.Productivity,
        "com.google.android.keep" to AppCategory.Productivity,
        "com.microsoft.todos" to AppCategory.Productivity,
        "com.microsoft.office.word" to AppCategory.Productivity,
        "com.microsoft.office.excel" to AppCategory.Productivity,
        "com.microsoft.office.powerpoint" to AppCategory.Productivity,
        "com.google.android.apps.docs" to AppCategory.Productivity,
        "com.notion.id" to AppCategory.Productivity,
        "com.evernote" to AppCategory.Productivity,
        "com.dropbox.android" to AppCategory.Productivity,

        // Finance — dense in TW and almost never categorised by the platform
        "com.jkos.app" to AppCategory.Finance,
        "tw.com.taishinbank.mobile" to AppCategory.Finance,
        "com.cathaybk.mymobibank.android" to AppCategory.Finance,
        "com.ctbcbank.mobilebank" to AppCategory.Finance,
        "tw.com.esunbank" to AppCategory.Finance,
        "com.chb.mobilebank" to AppCategory.Finance,
        "tw.com.fubon.mbank" to AppCategory.Finance,
        "com.easycard.app" to AppCategory.Finance,
        "com.pxpay.app" to AppCategory.Finance,
        "tw.com.line.pay" to AppCategory.Finance,

        // Shopping
        "com.shopee.tw" to AppCategory.Shopping,
        "com.pcstore.app" to AppCategory.Shopping,
        "tw.com.momo.app" to AppCategory.Shopping,
        "com.ruten.app" to AppCategory.Shopping,
        "com.amazon.mShop.android.shopping" to AppCategory.Shopping,
        "com.taobao.taobao" to AppCategory.Shopping,

        // Media
        "com.spotify.music" to AppCategory.Media,
        "com.google.android.youtube" to AppCategory.Media,
        "com.netflix.mediaclient" to AppCategory.Media,
        "com.kkbox.tw" to AppCategory.Media,
        "tv.twitch.android.app" to AppCategory.Media,
        "com.disney.disneyplus" to AppCategory.Media,

        // Photo
        "com.google.android.apps.photos" to AppCategory.Photo,
        "com.sec.android.app.camera" to AppCategory.Photo,
        "com.android.camera2" to AppCategory.Photo,
        "com.niksoftware.snapseed" to AppCategory.Photo,

        // Navigation
        "com.google.android.apps.maps" to AppCategory.Navigation,
        "com.waze" to AppCategory.Navigation,
        "com.ubercab" to AppCategory.Navigation,
        "com.taxi.taiwan" to AppCategory.Navigation,

        // News
        "com.google.android.apps.magazines" to AppCategory.News,
        "com.udn.news" to AppCategory.News,
        "com.chinatimes.app" to AppCategory.News,
        "com.ltn.newsapp" to AppCategory.News,

        // System / tools
        "com.android.settings" to AppCategory.System,
        "com.android.chrome" to AppCategory.Tools,
        "com.google.android.deskclock" to AppCategory.Tools,
        "com.sec.android.app.clockpackage" to AppCategory.Tools,
        "com.google.android.calculator" to AppCategory.Tools,
        "com.azure.authenticator" to AppCategory.Tools,
        "com.google.android.apps.authenticator2" to AppCategory.Tools,
    )

    /**
     * Categorises everything it can without inference. Apps it cannot place
     * are returned separately rather than being dumped in 其他, so the caller
     * can decide whether to spend a Nano batch on them (§8.1).
     */
    fun categorise(apps: List<AppEntry>): CategorisationResult {
        val known = mutableMapOf<String, AppCategory>()
        val unknown = mutableListOf<AppEntry>()

        for (app in apps) {
            val fromMap = knownPackages[app.packageName]
            if (fromMap != null) {
                known[app.packageName] = fromMap
                continue
            }

            val fromPlatform = platformCategory(app.packageName)
            if (fromPlatform != null) {
                known[app.packageName] = fromPlatform
                continue
            }

            if (isSystemApp(app.packageName)) {
                known[app.packageName] = AppCategory.System
                continue
            }

            unknown += app
        }

        return CategorisationResult(known, unknown)
    }

    /**
     * §8.1 — the one-tap button is the ideal place for on-device inference:
     * the user asked for it, the launcher is in the foreground, it is a batch,
     * and the answer caches per package. When no model is available every
     * remaining app simply lands in 其他, which is honest.
     */
    fun fallbackForUnknown(apps: List<AppEntry>): Map<String, AppCategory> =
        apps.associate { it.packageName to AppCategory.Other }

    private fun platformCategory(packageName: String): AppCategory? {
        val info = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull() ?: return null

        return when (info.category) {
            ApplicationInfo.CATEGORY_GAME -> AppCategory.Game
            ApplicationInfo.CATEGORY_AUDIO -> AppCategory.Media
            ApplicationInfo.CATEGORY_VIDEO -> AppCategory.Media
            ApplicationInfo.CATEGORY_IMAGE -> AppCategory.Photo
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.Social
            ApplicationInfo.CATEGORY_NEWS -> AppCategory.News
            ApplicationInfo.CATEGORY_MAPS -> AppCategory.Navigation
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.Productivity
            // CATEGORY_UNDEFINED is the common case, not an error.
            else -> null
        }
    }

    private fun isSystemApp(packageName: String): Boolean {
        val info = runCatching {
            context.packageManager.getApplicationInfo(packageName, 0)
        }.getOrNull() ?: return false
        return info.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
            info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
    }
}

data class CategorisationResult(
    val categorised: Map<String, AppCategory>,
    val unknown: List<AppEntry>,
) {
    /** How much of the library the deterministic ladder actually covered. */
    fun coverage(total: Int): Float =
        if (total == 0) 1f else categorised.size.toFloat() / total
}
