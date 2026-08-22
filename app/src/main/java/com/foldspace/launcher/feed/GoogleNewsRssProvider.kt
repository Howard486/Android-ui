package com.foldspace.launcher.feed

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Google News over its public RSS feed.
 *
 * The fallback for [GoogleOverlayFeedProvider], and in practice the one that
 * runs. It is not personalised Discover — it is the public headline feed — but
 * it needs no authorisation, cannot be withdrawn by a package-name check, and
 * means the leftmost page always has something on it.
 *
 * Parsed with the platform's own pull parser rather than a library: an RSS
 * channel of a few dozen `<item>`s does not justify a dependency, and the
 * launcher already has to be careful about method count and start-up cost.
 */
class GoogleNewsRssProvider(
    private val locale: Locale = Locale.getDefault(),
) : FeedProvider {

    override val name: String = "Google 新聞"

    /** No cheap way to know the network is usable; the load reports failure. */
    override fun isAvailable(): Boolean = true

    override suspend fun load(): FeedState = withContext(Dispatchers.IO) {
        val result = runCatching { fetch(feedUrl()) }
        val items = result.getOrNull()
        when {
            items == null -> FeedState.Unavailable("目前無法取得新聞，稍後再試。")
            items.isEmpty() -> FeedState.Unavailable("新聞來源沒有回傳內容。")
            else -> FeedState.Ready(items, name)
        }
    }

    private fun feedUrl(): String {
        // Google News wants a language, a country and a combined ceid. Falling
        // back to the Taiwan edition matches this build's audience rather than
        // silently serving US headlines.
        val language = locale.language.ifBlank { "zh" }
        val country = locale.country.ifBlank { "TW" }
        val ceid = "$country:$language"
        return "https://news.google.com/rss?hl=$language-$country&gl=$country&ceid=$ceid"
    }

    private fun fetch(url: String): List<FeedItem> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
        }

        return try {
            if (connection.responseCode !in 200..299) return emptyList()
            connection.inputStream.use(::parse)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(input: java.io.InputStream): List<FeedItem> {
        val parser = Xml.newPullParser().apply {
            setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            setInput(input, null)
        }

        val items = mutableListOf<FeedItem>()
        var title: String? = null
        var link: String? = null
        var source: String? = null
        var pubDate: String? = null
        var inItem = false

        while (parser.next() != XmlPullParser.END_DOCUMENT && items.size < MAX_ITEMS) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "item" -> {
                        inItem = true
                        title = null; link = null; source = null; pubDate = null
                    }

                    "title" -> if (inItem) title = parser.nextText().trim()
                    "link" -> if (inItem) link = parser.nextText().trim()
                    "source" -> if (inItem) source = parser.nextText().trim()
                    "pubDate" -> if (inItem) pubDate = parser.nextText().trim()
                }

                XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                    inItem = false
                    title?.takeIf { it.isNotBlank() }?.let {
                        items += FeedItem(
                            title = it,
                            source = source,
                            link = link,
                            publishedAtMillis = pubDate?.let(::parseRfc822),
                        )
                    }
                }
            }
        }
        return items
    }

    /** RSS timestamps are RFC 822; a failure here costs a relative time, not the story. */
    private fun parseRfc822(value: String): Long? =
        runCatching {
            SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).parse(value)?.time
        }.getOrNull()

    private companion object {
        const val TIMEOUT_MS = 8_000
        const val MAX_ITEMS = 25
        const val USER_AGENT = "FoldSpace/0.1 (Android launcher)"
    }
}
