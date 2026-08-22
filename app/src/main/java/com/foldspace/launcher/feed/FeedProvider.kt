package com.foldspace.launcher.feed

/** One story on the leftmost page. */
data class FeedItem(
    val title: String,
    val source: String?,
    val link: String?,
    val publishedAtMillis: Long?,
)

/** Why a provider cannot serve right now. */
sealed interface FeedState {
    data object Loading : FeedState

    data class Ready(val items: List<FeedItem>, val providerName: String) : FeedState

    data class Unavailable(val reason: String) : FeedState
}

/**
 * A source for the leftmost page.
 *
 * The page belongs to FoldSpace and the source is swappable, deliberately:
 * Google's Discover overlay is not something a third-party launcher can rely
 * on (see [GoogleOverlayFeedProvider]), and a page that is blank whenever
 * Google declines is worse than a page with a different source in it.
 */
interface FeedProvider {
    val name: String

    /** Cheap synchronous probe; must not touch the network. */
    fun isAvailable(): Boolean

    suspend fun load(): FeedState
}
