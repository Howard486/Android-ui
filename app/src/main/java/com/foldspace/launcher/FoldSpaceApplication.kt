package com.foldspace.launcher

import android.app.Application
import android.content.Context
import com.foldspace.launcher.ai.NanoAdapter
import com.foldspace.launcher.ai.PromptNanoAdapter
import com.foldspace.launcher.ai.TextInference
import com.foldspace.launcher.calendar.DeviceCalendarSource
import com.foldspace.launcher.context.ContextEngine
import com.foldspace.launcher.desktop.DesktopLauncher
import com.foldspace.launcher.microsoft.MicrosoftRepository
import com.foldspace.launcher.microsoft.TokenStore
import com.foldspace.launcher.quick.QuickController
import com.foldspace.launcher.usage.ScreenTimeSource
import com.foldspace.launcher.context.RuleEngine
import com.foldspace.launcher.core.launcher.HomeRoleManager
import com.foldspace.launcher.core.launcher.LauncherAppsRepository
import com.foldspace.launcher.feed.FeedRepository
import com.foldspace.launcher.feed.GoogleNewsRssProvider
import com.foldspace.launcher.feed.GoogleOverlayFeedProvider
import com.foldspace.launcher.home.AppCategorizer
import com.foldspace.launcher.home.HomeLayoutRepository
import com.foldspace.launcher.core.shortcuts.ShortcutRepository
import com.foldspace.launcher.home.GridSpec
import com.foldspace.launcher.home.HomeSurface
import com.foldspace.launcher.home.Posture
import com.foldspace.launcher.home.db.FoldSpaceDatabase
import com.foldspace.launcher.settings.GridChoice
import com.foldspace.launcher.pairs.SplitLauncher
import com.foldspace.launcher.ui.icons.IconPackRepository
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
     * §8 — the whole Nano pipeline, with no model behind it.
     *
     * Everything above the model is real: prompt construction, strict
     * parsing, the label whitelist, the confidence floor, batching, and the
     * refusal to let a model nominate 簡易. Only [TextInference] is a stub,
     * and swapping in AICore or ML Kit GenAI is a change to this one line.
     *
     * Keeping the stub means every caller exercises its fallback in day-one
     * testing rather than the first time it meets an unsupported device.
     */
    val nano: NanoAdapter = PromptNanoAdapter(TextInference.None)

    /**
     * Held rather than inlined so the ViewModel can keep its rules in step
     * with the user's. It used to be constructed anonymously with an empty
     * list, which made §7.1 level 2 unreachable.
     */
    val ruleEngine = RuleEngine()

    val contextEngine = ContextEngine(
        scope = appScope,
        ruleEngine = ruleEngine,
        nano = nano,
    )

    /** Third-party icon packs, in the de-facto ADW/Nova format. */
    val iconPacks = IconPackRepository(context)

    /** §4 — two apps side by side, as far as the platform permits. */
    val splitLauncher = SplitLauncher(context, launcherApps)

    /**
     * Today's calendar, from the device rather than from an account.
     *
     * The zero-setup route: no registration, no token, no network. Graph is
     * the upgrade, and the only path to To Do.
     */
    val deviceCalendar = DeviceCalendarSource(context)

    /** Calendar and tasks from Microsoft Graph. No model, no cache, no notes. */
    val microsoft = MicrosoftRepository(context, TokenStore(context))

    /** Today's screen time, read on demand and never stored. */
    val screenTime = ScreenTimeSource(context)

    /** Torch, volume and brightness — and honesty about the rest. */
    val quickControls = QuickController(context)

    /** Opens apps as windows on the unfolded screen, where the device allows. */
    val desktopLauncher = DesktopLauncher(context)

    /** §5.3 — the shortcuts an app publishes, and pin requests it makes. */
    val shortcuts = ShortcutRepository(context)

    private val appResources = context.resources

    /**
     * Cell size in dp, worked out from the screen rather than measured.
     *
     * [com.foldspace.launcher.PinRequestActivity] needs a widget's default
     * span but never draws a grid, so it has nothing to measure. This is the
     * same arithmetic the grid does, one step earlier.
     */
    fun cellSizeDp(posture: Posture, choice: GridChoice): Pair<Int, Int> {
        val grid = GridSpec.of(HomeSurface.Desktop, posture, choice)
        val metrics = appResources.displayMetrics
        val density = metrics.density.takeIf { it > 0f } ?: 1f
        val widthDp = (metrics.widthPixels / density).toInt()
        val heightDp = (metrics.heightPixels / density).toInt()
        return (widthDp / grid.columns).coerceAtLeast(1) to
            (heightDp / grid.rows).coerceAtLeast(1)
    }
}

/** Convenience accessor; every call site already holds a Context. */
val Context.appContainer: AppContainer
    get() = (applicationContext as FoldSpaceApplication).container
