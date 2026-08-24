package com.foldspace.launcher.microsoft

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the Microsoft section can be showing. */
sealed interface MicrosoftState {
    /** No client id has been configured, so nothing can be attempted. */
    data object NotConfigured : MicrosoftState

    data object SignedOut : MicrosoftState

    data object Loading : MicrosoftState

    data class Ready(
        val events: List<GraphEvent>,
        val tasks: List<GraphTask>,
    ) : MicrosoftState

    data class Failed(val reason: String) : MicrosoftState
}

/**
 * Calendar and tasks from Microsoft Graph.
 *
 * Two things this deliberately does not do:
 *
 *  - **Sticky Notes.** There is no public Graph API for them. The Outlook REST
 *    v2 route that used to serve them is deprecated with no documented
 *    replacement, so the page says they are unavailable rather than quietly
 *    omitting a feature Microsoft Launcher advertises.
 *  - **Copilot.** No public Android surface exists for the launcher-feed
 *    Copilot. Same treatment as Gemini Nano: not faked.
 *
 * Everything here is read-only, and nothing is cached to disk. §16.1 applies to
 * somebody's calendar more than to anything else in this app.
 */
class MicrosoftRepository(
    private val context: Context,
    private val tokens: TokenStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {

    private var pendingRequest: AuthRequest? = null

    /**
     * Why the last sign-in did not work.
     *
     * Held here rather than shown once in a toast. The browser hands control
     * back to a throwaway activity, so a toast is the only thing the old code
     * could show — and "invalid_request" flashing over the dock for two seconds
     * told the user nothing and left the card still saying 連結帳戶, as though
     * nothing had happened.
     */
    @Volatile
    var lastError: String? = null
        private set

    fun clearError() {
        lastError = null
    }

    /** Starts sign-in in a browser. Returns false when there is no client id. */
    fun beginSignIn(clientId: String?): Boolean {
        val id = clientId?.trim()?.takeIf { it.isNotBlank() } ?: return false
        // A retry starts clean, or the previous reason would sit on the card
        // through a sign-in that is still in the browser.
        lastError = null
        val request = MicrosoftAuth.buildRequest(id)
        pendingRequest = request
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(request.authorizeUrl))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent); true }.getOrDefault(false)
    }

    /**
     * Completes sign-in from the redirect.
     *
     * The `state` check lives in [MicrosoftAuth.codeFromRedirect] and is not
     * optional: without it any app that can fire an intent at the custom scheme
     * could have its own authorization code exchanged here.
     */
    suspend fun completeSignIn(clientId: String?, redirect: String): String? {
        val failure = attemptSignIn(clientId, redirect)
        lastError = failure
        return failure
    }

    private suspend fun attemptSignIn(clientId: String?, redirect: String): String? {
        val id = clientId?.trim()?.takeIf { it.isNotBlank() } ?: return "尚未設定用戶端 ID。"
        val request = pendingRequest ?: return "沒有進行中的登入。"
        pendingRequest = null

        MicrosoftAuth.errorFromRedirect(redirect)?.let { return MicrosoftAuth.explain(it) }
        val code = MicrosoftAuth.codeFromRedirect(redirect, request.state)
            ?: return "登入回應無法驗證，已忽略。"

        val body = MicrosoftAuth.tokenRequestBody(id, code, request.codeVerifier)
        val response = withContext(Dispatchers.IO) { post(MicrosoftAuth.TOKEN_ENDPOINT, body) }
            ?: return "無法連線到 Microsoft。"

        val parsed = parseTokens(response) ?: return "Microsoft 沒有回傳權杖。"
        tokens.write(parsed)
        return null
    }

    suspend fun signOut() {
        lastError = null
        tokens.clear()
    }

    suspend fun isSignedIn(): Boolean = tokens.read()?.refreshToken != null

    /** Today's events and open tasks, or a reason there are none. */
    suspend fun load(clientId: String?): MicrosoftState {
        val id = clientId?.trim()?.takeIf { it.isNotBlank() }
            ?: return MicrosoftState.NotConfigured
        // A failed attempt outranks "signed out": the difference between
        // "you have not connected an account" and "connecting failed, and
        // here is why" is the whole of what the user needs to know.
        val access = accessToken(id)
            ?: return lastError?.let(MicrosoftState::Failed) ?: MicrosoftState.SignedOut

        return withContext(Dispatchers.IO) {
            val events = get(calendarUrl(), access)?.let(GraphModels::events).orEmpty()
            val tasks = GraphModels.taskLists(get(TASK_LISTS_URL, access).orEmpty())
                .take(MAX_TASK_LISTS)
                .flatMap { (listId, listName) ->
                    get(tasksUrl(listId), access)
                        ?.let { GraphModels.tasks(it, listName) }
                        .orEmpty()
                }
            MicrosoftState.Ready(events, tasks.take(MAX_TASKS))
        }
    }

    /**
     * A usable access token, refreshing once if the stored one has expired.
     *
     * Exactly once. A refresh that fails is a signed-out state, not something
     * to retry in a loop against an endpoint that has already said no.
     */
    private suspend fun accessToken(clientId: String): String? {
        val stored = tokens.read() ?: return null
        if (stored.accessToken.isNotBlank() && !stored.isExpired(clock())) {
            return stored.accessToken
        }

        val refresh = stored.refreshToken ?: return null
        val response = withContext(Dispatchers.IO) {
            post(MicrosoftAuth.TOKEN_ENDPOINT, MicrosoftAuth.refreshRequestBody(clientId, refresh))
        } ?: return null

        val parsed = parseTokens(response) ?: return null
        // Carry the old refresh token forward when the response omits one.
        tokens.write(parsed.copy(refreshToken = parsed.refreshToken ?: refresh))
        return parsed.accessToken
    }

    private fun parseTokens(body: String): MicrosoftTokens? = runCatching {
        val json = org.json.JSONObject(body)
        MicrosoftAuth.tokensFrom(
            accessToken = json.optString("access_token").takeIf { it.isNotBlank() },
            refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() },
            expiresInSeconds = json.optLong("expires_in").takeIf { it > 0L },
            now = clock(),
        )
    }.getOrNull()

    private fun calendarUrl(): String {
        val start = Calendar.getInstance().apply {
            timeInMillis = clock()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
        return "https://graph.microsoft.com/v1.0/me/calendarView" +
            "?startDateTime=" + iso(start.time) +
            "&endDateTime=" + iso(end.time) +
            "&\$orderby=start/dateTime&\$top=" + MAX_EVENTS
    }

    private fun tasksUrl(listId: String): String =
        "https://graph.microsoft.com/v1.0/me/todo/lists/$listId/tasks" +
            "?\$filter=status%20ne%20'completed'&\$top=" + MAX_TASKS

    private fun iso(date: Date): String = SimpleDateFormat(ISO_FORMAT, Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(date)

    private fun get(url: String, accessToken: String): String? = request(url) { connection ->
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
    }

    private fun post(url: String, body: String): String? = request(url) { connection ->
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
    }

    private fun request(url: String, configure: (HttpURLConnection) -> Unit): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
            configure(this)
        }
        try {
            if (connection.responseCode !in 200..299) {
                // The error body is read and dropped rather than surfaced: it
                // can name the signed-in account, and this is a launcher, not
                // a diagnostic tool.
                connection.errorStream?.use { it.readBytes() }
                return@runCatching null
            }
            connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    private companion object {
        const val TASK_LISTS_URL = "https://graph.microsoft.com/v1.0/me/todo/lists"
        const val ISO_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'"
        const val TIMEOUT_MS = 12_000
        const val MAX_EVENTS = 12
        const val MAX_TASKS = 15
        const val MAX_TASK_LISTS = 3
    }
}
