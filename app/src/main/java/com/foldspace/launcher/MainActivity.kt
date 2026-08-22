package com.foldspace.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
 */
class MainActivity : ComponentActivity() {

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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

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
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResumed()
    }

    override fun onPause() {
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
}
