package com.foldspace.launcher.feed

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serves the leftmost page from the first provider that will actually deliver.
 *
 * Loads are demand-driven and throttled: the feed page is one swipe away at
 * all times, so refetching on every visit would turn an idle launcher into a
 * periodic network client, which §12.1 exists to prevent.
 */
class FeedRepository(
    private val providers: List<FeedProvider>,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private val _state = MutableStateFlow<FeedState>(FeedState.Loading)
    val state: StateFlow<FeedState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var lastLoadedAt = 0L

    /** Which provider declined and why, for the settings screen to report. */
    private val _providerNotes = MutableStateFlow<List<String>>(emptyList())
    val providerNotes: StateFlow<List<String>> = _providerNotes.asStateFlow()

    suspend fun refreshIfStale(force: Boolean = false) {
        mutex.withLock {
            val now = clock()
            if (!force && now - lastLoadedAt < THROTTLE_MS && _state.value is FeedState.Ready) return
            _state.value = FeedState.Loading

            val notes = mutableListOf<String>()
            for (provider in providers) {
                if (!provider.isAvailable()) {
                    // A provider that declines up front still explains itself;
                    // "the page is empty" with no reason is the failure mode
                    // this whole arrangement exists to avoid.
                    (provider as? GoogleOverlayFeedProvider)?.load()?.let { declined ->
                        (declined as? FeedState.Unavailable)?.let { notes += it.reason }
                    }
                    continue
                }
                when (val result = provider.load()) {
                    is FeedState.Ready -> {
                        _state.value = result
                        _providerNotes.value = notes
                        lastLoadedAt = now
                        return
                    }

                    is FeedState.Unavailable -> notes += "${provider.name}：${result.reason}"
                    FeedState.Loading -> Unit
                }
            }

            _providerNotes.value = notes
            _state.value = FeedState.Unavailable(
                notes.lastOrNull() ?: "沒有可用的新聞來源。",
            )
        }
    }

    private companion object {
        /** Headlines do not change usefully faster than this. */
        const val THROTTLE_MS = 15 * 60 * 1000L
    }
}
