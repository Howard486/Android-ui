package com.foldspace.launcher.feed

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
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

    /**
     * Tries the localised edition, then the bare feed.
     *
     * Every failure is described rather than collapsed into "try again later":
     * the first version of this page said exactly that for a missing INTERNET
     * permission, and a message that fits every cause points at none of them.
     */
    override suspend fun load(): FeedState = withContext(Dispatchers.IO) {
        var lastFailure: String? = null

        for (url in candidateUrls()) {
            val attempt = runCatching { fetch(url) }
            val items = attempt.getOrNull()
            when {
                items == null -> lastFailure = describe(attempt.exceptionOrNull())
                items.isEmpty() -> lastFailure = "來源沒有回傳任何項目。"
                else -> return@withContext FeedState.Ready(items, name)
            }
        }

        FeedState.Unavailable(lastFailure ?: "目前無法取得新聞。")
    }

    /**
     * Editions first, bare feed last.
     *
     * Google News keys a Chinese edition on a script-qualified tag —
     * `ceid=TW:zh-Hant`, not `TW:zh`. The unqualified form is what this built
     * before, and it does not resolve to an edition, so nothing came back.
     */
    internal fun candidateUrls(): List<String> {
        val language = locale.language.ifBlank { "zh" }
        val country = locale.country.ifBlank { "TW" }.uppercase(Locale.US)
        val edition = editionTag(language, country)
        return listOf(
            "https://news.google.com/rss?hl=$language-$country&gl=$country&ceid=$country:$edition",
            // No edition at all still returns headlines, which beats a blank
            // page when the locale is one Google does not publish for.
            "https://news.google.com/rss",
        )
    }

    /** The `ceid` language half: Chinese needs its script, others do not. */
    private fun editionTag(language: String, country: String): String =
        if (!language.equals("zh", ignoreCase = true)) {
            language
        } else {
            when (country) {
                "TW", "HK", "MO" -> "zh-Hant"
                else -> "zh-Hans"
            }
        }

    private fun fetch(url: String): List<FeedItem> {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml")
        }

        return try {
            val code = connection.responseCode
            // Thrown rather than returned empty: the caller has to be able to
            // tell "the server said no" from "the server had nothing".
            if (code !in 200..299) throw IOException("HTTP $code")
            connection.inputStream.use(::parse)
        } finally {
            connection.disconnect()
        }
    }

    private fun describe(error: Throwable?): String = when (error) {
        null -> "目前無法取得新聞。"
        // The one failure a user cannot do anything about, and the one this
        // build actually hit.
        is SecurityException -> "應用程式沒有網路權限，無法取得新聞。"
        is UnknownHostException -> "連不到 news.google.com，請確認網路連線。"
        is SocketTimeoutException -> "連線逾時，稍後再試。"
        is IOException -> error.message?.let { "連線失敗（$it）" } ?: "連線失敗。"
        else -> error.message?.let { "無法取得新聞（$it）" } ?: "無法取得新聞。"
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
