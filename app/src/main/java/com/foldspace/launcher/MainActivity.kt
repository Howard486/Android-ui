package com.foldspace.launcher

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.fragment.app.FragmentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.foldspace.launcher.notifications.FoldSpaceNotificationListener
import com.foldspace.launcher.ui.FoldSpaceRoot
import com.foldspace.launcher.ui.LauncherViewModel
import com.foldspace.launcher.ui.layout.FoldStateTracker
import com.foldspace.launcher.ui.theme.FoldSpaceTheme
import com.foldspace.launcher.ui.theme.Themes
import com.foldspace.launcher.ui.theme.effectiveMotion
import kotlinx.coroutines.launch

/**
 * The HOME activity.
 *
 * Two things make this different from an ordinary Compose activity:
 *  - it is `singleTask` + `stateNotNeeded` (see the manifest), so it must be
 *    correct after being killed and relaunched with nothing restored;
 *  - it handles fold changes itself via `configChanges`, so the fold posture
 *    arrives as a [FoldStateTracker] emission rather than as a recreation.
 *
 * A `FragmentActivity` rather than a plain `ComponentActivity` for one reason:
 * `BiometricPrompt` is implemented as a fragment and will not attach to
 * anything else. It costs the fragment manager and nothing else — no fragment
 * is ever added by this app.
 */
class MainActivity : FragmentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    /**
     * §5.1 — the result is ignored deliberately: the role may be granted,
     * declined, or changed later in Settings, so the truth always comes from
     * re-reading `isRoleHeld` on resume rather than from this callback.
     */
    private val roleRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refreshPermissions()
        }

    /**
     * §13 — the bind-consent and configure dialogs can only be launched from
     * an Activity, so the ViewModel emits a request and this runs it. The
     * provider is remembered across the round trip because the result carries
     * only the widget id.
     */
    private var pendingWidgetInfo: AppWidgetProviderInfo? = null

    private val widgetBindRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val info = pendingWidgetInfo ?: return@registerForActivityResult
            val id = result.data?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
            viewModel.onWidgetBindResult(id, info, result.resultCode == Activity.RESULT_OK)
        }

    private val widgetConfigureRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val info = pendingWidgetInfo ?: return@registerForActivityResult
            val id = result.data?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
            viewModel.onWidgetConfigureResult(id, info, result.resultCode == Activity.RESULT_OK)
        }

    /**
     * §16 — backup goes through the Storage Access Framework, so FoldSpace
     * needs no storage permission at all and the user picks exactly where the
     * file lands.
     */
    private val exportRequest =
        registerForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME)) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val text = viewModel.exportLayoutText()
                val written = runCatching {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                }.isSuccess
                viewModel.reportBackupResult(written)
            }
        }

    private val importRequest =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            val text = runCatching {
                contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (text == null) {
                viewModel.reportBackupResult(false)
            } else {
                viewModel.importLayoutText(text)
            }
        }

    /**
     * §16 — READ_CALENDAR is a runtime permission and was never requested, so
     * the declaration bought nothing. Asked for only when the user turns the
     * calendar signal on, never at launch.
     */
    private val calendarPermissionRequest =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.refreshPermissions()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            viewModel.widgetRequests.collect(::runWidgetRequest)
        }

        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()

            // §4 — one tracker per activity, restarted with the composition.
            val tracker = remember { FoldStateTracker(this) }
            androidx.compose.runtime.LaunchedEffect(tracker) {
                tracker.states().collect(viewModel::onWindowStateChanged)
            }

            val tokens = Themes.of(state.activeTheme)
            FoldSpaceTheme(
                tokens = tokens,
                motionLevel = effectiveMotion(
                    tokens,
                    state.settings.powerMode,
                    state.powerSaveActive,
                ),
            ) {
                FoldSpaceRoot(
                    viewModel = viewModel,
                    onOpenNotificationSettings = ::openNotificationListenerSettings,
                    onOpenUsageSettings = ::openUsageAccessSettings,
                    onRequestDefaultHome = ::requestDefaultHome,
                    onExpandStatusBar = ::expandStatusBar,
                    onOpenLink = ::openLink,
                    onOpenPackage = ::openPackage,
                    onExportLayout = ::exportLayout,
                    onImportLayout = ::importLayout,
                    onRequestCalendarAccess = ::requestCalendarAccess,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumed()
        // §13 — widgets only refresh while the host listens, and the host only
        // listens while the launcher is on screen. A backgrounded launcher
        // taking widget updates is the standing cost §12 exists to avoid.
        viewModel.widgetHost().startListening()
    }

    override fun onPause() {
        viewModel.widgetHost().stopListening()
        // §8.2 — the moment we are no longer foreground, Nano becomes
        // ineligible. Reporting that here is what enforces it.
        viewModel.onPaused()
        super.onPause()
    }

    /**
     * Pressing Home while already home should close every overlay rather than
     * do nothing, which is what users expect from the physical gesture.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.setDrawerOpen(false)
        viewModel.setNotificationCenterOpen(false)
        viewModel.setSettingsOpen(false)
    }

    private fun requestDefaultHome() {
        val intent = viewModel.homeRoleIntent()
        if (intent != null) {
            roleRequest.launch(intent)
        } else {
            // §5.1 fallback for devices without the HOME role.
            startActivity(viewModel.homeSettingsIntent())
        }
    }

    private fun runWidgetRequest(request: LauncherViewModel.WidgetSystemRequest) {
        when (request) {
            is LauncherViewModel.WidgetSystemRequest.Bind -> {
                pendingWidgetInfo = request.info
                runCatching {
                    widgetBindRequest.launch(
                        viewModel.widgetHost()
                            .bindPermissionIntent(request.appWidgetId, request.info),
                    )
                }
            }

            is LauncherViewModel.WidgetSystemRequest.Configure -> {
                pendingWidgetInfo = request.info
                val configure: ComponentName = request.info.configure ?: run {
                    viewModel.onWidgetConfigureResult(request.appWidgetId, request.info, true)
                    return
                }
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(configure)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, request.appWidgetId)
                runCatching { widgetConfigureRequest.launch(intent) }
                    .onFailure {
                        // A provider can declare a configure activity that
                        // refuses to start. Placing it unconfigured beats
                        // dropping the widget the user just chose.
                        viewModel.onWidgetConfigureResult(request.appWidgetId, request.info, true)
                    }
            }
        }
    }

    private fun requestCalendarAccess() {
        runCatching {
            calendarPermissionRequest.launch(android.Manifest.permission.READ_CALENDAR)
        }
    }

    private fun exportLayout() {
        runCatching { exportRequest.launch(defaultBackupName()) }
    }

    private fun importLayout() {
        // Every mime type: some file managers hand back application/octet-stream
        // for an unknown extension, and refusing our own file would be absurd.
        runCatching { importRequest.launch(arrayOf("*/*")) }
    }

    private fun defaultBackupName(): String {
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
            .format(java.util.Date())
        return "foldspace-layout-" + stamp + ".txt"
    }

    /** Opens a feed story in whatever the user's browser is. */
    private fun openLink(url: String) {
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * §6 — a work item leads back to the app that raised it. There is no
     * public deep link into an Outlook message, so this opens the app itself
     * rather than pretending to land on the item.
     */
    private fun openPackage(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun openNotificationListenerSettings() {
        runCatching { startActivity(FoldSpaceNotificationListener.settingsIntent()) }
    }

    private fun openUsageAccessSettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    /**
     * §6 — swipe-down hands off to the system shade rather than FoldSpace
     * drawing its own. There is no public API for this, so it is a reflective
     * call that fails silently; the gesture simply does nothing where the OEM
     * does not allow it, which is the correct outcome for a gesture we do not
     * own.
     */
    private fun expandStatusBar() {
        lifecycleScope.launch {
            runCatching {
                val service = getSystemService("statusbar")
                val method = Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel")
                method.invoke(service)
            }
        }
    }

    private companion object {
        const val BACKUP_MIME = "text/plain"
    }
}
