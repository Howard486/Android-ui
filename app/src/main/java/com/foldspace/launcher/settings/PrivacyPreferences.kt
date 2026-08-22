package com.foldspace.launcher.settings

/**
 * §10.4 — "sensitive app: show icon/count only, do not analyse content".
 *
 * This is read on the notification hot path, from the listener service, which
 * may be bound before any coroutine has had a chance to read DataStore. So it
 * is a plain volatile snapshot: [SettingsRepository] pushes into it, and the
 * listener only ever reads it.
 *
 * Failing closed matters here — until settings have loaded we assume nothing
 * is analysable rather than analysing something the user excluded.
 */
object PrivacyPreferences {

    @Volatile
    private var loaded: Boolean = false

    @Volatile
    private var excludedPackages: Set<String> = emptySet()

    @Volatile
    private var contentAnalysisEnabled: Boolean = true

    fun update(excluded: Set<String>, analysisEnabled: Boolean) {
        excludedPackages = excluded
        contentAnalysisEnabled = analysisEnabled
        loaded = true
    }

    fun analysesContentOf(packageName: String): Boolean =
        loaded && contentAnalysisEnabled && packageName !in excludedPackages

    fun isExcluded(packageName: String): Boolean = packageName in excludedPackages
}
