package com.foldspace.launcher.microsoft

import org.json.JSONArray
import org.json.JSONObject

/** One calendar entry today. */
data class GraphEvent(
    val subject: String,
    val start: String,
    val end: String,
    val isAllDay: Boolean,
    val location: String?,
)

/** One task still open. */
data class GraphTask(
    val title: String,
    val listName: String,
    val dueDate: String?,
    val importance: String?,
)

/**
 * Reads what Graph returns, and refuses what it does not.
 *
 * Written against the shape rather than trusting it: a `value` that is not an
 * array, an entry that is not an object, a subject that is missing — all of
 * those are dropped, one item at a time, rather than throwing away the whole
 * response or crashing the page. The same property the text codecs in this
 * project already have: a damaged record costs only that record.
 */
object GraphModels {

    fun events(body: String): List<GraphEvent> = items(body).mapNotNull { item ->
        val subject = item.optString("subject").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        GraphEvent(
            subject = subject,
            start = item.optJSONObject("start")?.optString("dateTime").orEmpty(),
            end = item.optJSONObject("end")?.optString("dateTime").orEmpty(),
            isAllDay = item.optBoolean("isAllDay", false),
            location = item.optJSONObject("location")
                ?.optString("displayName")
                ?.takeIf { it.isNotBlank() },
        )
    }

    fun tasks(body: String, listName: String): List<GraphTask> = items(body).mapNotNull { item ->
        val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        // Completed tasks are filtered server-side, but a server-side filter is
        // a request that can be ignored; checking here costs one comparison.
        if (item.optString("status") == "completed") return@mapNotNull null
        GraphTask(
            title = title,
            listName = listName,
            dueDate = item.optJSONObject("dueDateTime")
                ?.optString("dateTime")
                ?.takeIf { it.isNotBlank() },
            importance = item.optString("importance").takeIf { it.isNotBlank() },
        )
    }

    /** Task list ids and names, for the second call. */
    fun taskLists(body: String): List<Pair<String, String>> = items(body).mapNotNull { item ->
        val id = item.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        id to item.optString("displayName").ifBlank { "工作" }
    }

    /** `HH:mm` from a Graph timestamp, or null when it is not one. */
    fun timeOf(dateTime: String?): String? {
        if (dateTime.isNullOrBlank()) return null
        val time = dateTime.substringAfter('T', "").take(5)
        return time.takeIf { it.length == 5 && it[2] == ':' }
    }

    private fun items(body: String): List<JSONObject> = runCatching {
        val array: JSONArray = JSONObject(body).optJSONArray("value") ?: return emptyList()
        (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }.getOrDefault(emptyList())
}
