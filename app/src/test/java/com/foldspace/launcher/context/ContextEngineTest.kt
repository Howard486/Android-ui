package com.foldspace.launcher.context

import com.foldspace.launcher.ai.NanoAdapter
import com.foldspace.launcher.ai.NanoContextResult
import com.foldspace.launcher.ai.NanoNotificationInput
import com.foldspace.launcher.ai.NanoNotificationResult
import com.foldspace.launcher.ai.NanoUnavailableReason
import com.foldspace.launcher.spaces.SpaceId
import com.foldspace.launcher.ui.layout.LayoutMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * §8.2, §12.4 and §21.2 all reduce to the same three claims about the engine:
 * Nano is never reached in the background, never reached twice for the same
 * fingerprint, and never allowed to switch a Space by itself.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ContextEngineTest {

    private class CountingNano(
        private var available: NanoUnavailableReason = NanoUnavailableReason.Available,
        private val result: NanoContextResult? =
            NanoContextResult(SpaceId.Work, 0.92f, "WORK_APPS_AND_CALENDAR"),
    ) : NanoAdapter {
        var calls = 0
            private set

        override fun availability() = available

        override suspend fun classifyContext(snapshot: ContextSnapshot): NanoContextResult? {
            calls++
            return result
        }

        override suspend fun classifyNotifications(
            items: List<NanoNotificationInput>,
        ): List<NanoNotificationResult> = emptyList()
    }

    private fun engine(
        nano: NanoAdapter,
        scope: TestScope,
        rules: List<AutomationRule> = emptyList(),
    ) = ContextEngine(
        scope = scope,
        ruleEngine = RuleEngine(rules),
        nano = nano,
        clock = { 0L },
    )

    /** Drives the engine into the "rules declined, Nano may run" state. */
    private fun ContextEngine.enterAmbiguousForeground() {
        // Weekend afternoon: the rule engine scores nothing above threshold.
        onEvent(ContextEvent.TimeChanged(hourOfDay = 14, isWeekday = false))
        onEvent(ContextEvent.FoldChanged(LayoutMode.Expanded))
        onEvent(ContextEvent.LauncherForeground(true, hourOfDay = 14, isWeekday = false))
    }

    @Test
    fun `nano is not consulted while the launcher is in the background`() = runTest {
        val nano = CountingNano()
        val engine = engine(nano, TestScope(UnconfinedTestDispatcher(testScheduler)))

        engine.onEvent(ContextEvent.LauncherForeground(false, hourOfDay = 14, isWeekday = false))
        engine.onEvent(ContextEvent.FoldChanged(LayoutMode.Expanded))

        assertEquals(0, nano.calls)
    }

    @Test
    fun `nano is not consulted in battery saver`() = runTest {
        val nano = CountingNano()
        val engine = engine(nano, TestScope(UnconfinedTestDispatcher(testScheduler)))

        engine.enterAmbiguousForeground()
        val before = nano.calls
        engine.onEvent(ContextEvent.PowerSaveChanged(true))

        // The power-save event changes the fingerprint, so the engine
        // re-evaluates — and must decline to call Nano this time.
        assertEquals(before, nano.calls)
    }

    @Test
    fun `an unsupported device never reaches nano`() = runTest {
        val nano = CountingNano(available = NanoUnavailableReason.DeviceNotSupported)
        val engine = engine(nano, TestScope(UnconfinedTestDispatcher(testScheduler)))

        engine.enterAmbiguousForeground()

        assertEquals(0, nano.calls)
        assertNull(engine.suggestion.value)
    }

    @Test
    fun `a nano classification is offered, never applied`() = runTest {
        val nano = CountingNano()
        val engine = engine(nano, TestScope(UnconfinedTestDispatcher(testScheduler)))

        engine.enterAmbiguousForeground()

        assertEquals(SpaceId.Work, engine.suggestion.value?.space)
    }

    @Test
    fun `nano may not nominate the simplified Space`() = runTest {
        // §7.1 — 簡易 is the user's choice about their own interface. A model
        // deciding you should be moved into an easier one is a judgement it
        // has no business making, however confident it is.
        val nano = CountingNano(
            result = NanoContextResult(SpaceId.Simple, 0.99f, "SIMPLE"),
        )
        val engine = engine(nano, TestScope(UnconfinedTestDispatcher(testScheduler)))

        engine.enterAmbiguousForeground()

        assertEquals(1, nano.calls)
        assertNull(engine.suggestion.value)
    }

    @Test
    fun `a manual pick clears the suggestion`() = runTest {
        val nano = CountingNano()
        val engine = engine(nano, TestScope(UnconfinedTestDispatcher(testScheduler)))

        engine.enterAmbiguousForeground()
        engine.setUserOverride(SpaceId.Simple)

        assertNull(engine.suggestion.value)
    }

    @Test
    fun `manual-only mode suppresses evaluation entirely`() = runTest {
        val nano = CountingNano()
        val engine = engine(nano, TestScope(UnconfinedTestDispatcher(testScheduler)))

        engine.setSwitchMode(SwitchMode.ManualOnly)
        engine.enterAmbiguousForeground()

        assertEquals(0, nano.calls)
        assertNull(engine.suggestion.value)
    }
}
