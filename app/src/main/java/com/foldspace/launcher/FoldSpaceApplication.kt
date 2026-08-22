package com.foldspace.launcher

import android.app.Application
import android.content.Context
import com.foldspace.launcher.ai.NanoAdapter
import com.foldspace.launcher.context.ContextEngine
import com.foldspace.launcher.context.RuleEngine
import com.foldspace.launcher.core.launcher.HomeRoleManager
import com.foldspace.launcher.core.launcher.LauncherAppsRepository
import com.foldspace.launcher.feed.FeedRepository
import com.foldspace.launcher.feed.GoogleNewsRssProvider
import com.foldspace.launcher.feed.GoogleOverlayFeedProvider
import com.foldspace.launcher.home.AppCategorizer
import com.foldspace.launcher.home.HomeLayoutRepository
import com.foldspace.launcher.home.db.FoldSpaceDatabase
import com.foldspace.launcher.widgets.WidgetHostController
import com.foldspace.launcher.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency graph.
 *
 * The spec suggests Hilt or Koin (§17.1), and either is the right call once
 * there is more than one entry point. For V0.1 there is exactly one Activity
 * and one Service, and the graph below is the whole app — a DI framework here
 * would add build time and indirection without removing any.
 */
class FoldSpaceApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {

    /** Outlives any Activity; nothing here should be torn down on a fold. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context, appScope)

    val homeRole = HomeRoleManager(context)

    val launcherApps = LauncherAppsRepository(context, appScope)

    private val database = FoldSpaceDatabase.get(context)

    /** The home-screen arrangement: pages, cells, folders, widget bindings. */
    val homeLayout = HomeLayoutRepository(
        dao = database.homeItemDao(),
        pageDao = database.homePageDao(),
        installedApps = launcherApps.apps,
    )

    val categorizer = AppCategorizer(context)

    /** §13 — third-party App Widget hosting. */
    val widgetHost = WidgetHostController(context)

    /**
     * The leftmost page. Google's overlay is asked first and expected to
     * decline; the RSS provider is what actually serves.
     */
    val feed = FeedRepository(
        providers = listOf(
            GoogleOverlayFeedProvider(context),
            GoogleNewsRssProvider(),
        ),
    )

    /**
     * §8 — V0.1 ships with no on-device model behind this. Swapping in the ML
     * Kit GenAI implementation (V0.5, §20.2) is a one-line change here.
     */
    val nano: NanoAdapter = NanoAdapter.Unsupported

    val contextEngine = ContextEngine(
        scope = appScope,
        ruleEngine = RuleEngine(),
        nano = nano,
    )
}

/** Convenience accessor; every call site already holds a Context. */
val Context.appContainer: AppContainer
    get() = (applicationContext as FoldSpaceApplication).container
