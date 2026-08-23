package com.foldspace.launcher.microsoft

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GraphModelsTest {

    private val eventsBody = """
        {"value":[
          {"subject":"設計評審","isAllDay":false,
           "start":{"dateTime":"2026-08-23T14:00:00.0000000","timeZone":"UTC"},
           "end":{"dateTime":"2026-08-23T15:00:00.0000000","timeZone":"UTC"},
           "location":{"displayName":"會議室 A"}},
          {"subject":"全天活動","isAllDay":true,
           "start":{"dateTime":"2026-08-23T00:00:00.0000000"},
           "end":{"dateTime":"2026-08-24T00:00:00.0000000"}}
        ]}
    """.trimIndent()

    @Test
    fun `events are read with their times and location`() {
        val events = GraphModels.events(eventsBody)
        assertEquals(2, events.size)
        assertEquals("設計評審", events[0].subject)
        assertEquals("會議室 A", events[0].location)
        assertEquals("14:00", GraphModels.timeOf(events[0].start))
        assertTrue(events[1].isAllDay)
        assertNull(events[1].location)
    }

    @Test
    fun `an entry with no subject is dropped, not rendered blank`() {
        val body = """{"value":[{"subject":""},{"subject":"真的"},{"nothing":1}]}"""
        assertEquals(listOf("真的"), GraphModels.events(body).map { it.subject })
    }

    @Test
    fun `malformed json yields nothing rather than throwing`() {
        assertTrue(GraphModels.events("not json at all").isEmpty())
        assertTrue(GraphModels.events("").isEmpty())
        assertTrue(GraphModels.tasks("{oops", "L").isEmpty())
        assertTrue(GraphModels.taskLists("[]").isEmpty())
    }

    @Test
    fun `a response with no value array yields nothing`() {
        assertTrue(GraphModels.events("""{"error":{"code":"InvalidAuthenticationToken"}}""").isEmpty())
    }

    @Test
    fun `a value that is not an array is refused`() {
        assertTrue(GraphModels.events("""{"value":"surprise"}""").isEmpty())
    }

    @Test
    fun `tasks carry their list name and due date`() {
        val body = """
            {"value":[
              {"title":"寄出報價","status":"notStarted","importance":"high",
               "dueDateTime":{"dateTime":"2026-08-24T00:00:00.0000000"}},
              {"title":"已完成的","status":"completed"}
            ]}
        """.trimIndent()
        val tasks = GraphModels.tasks(body, "工作清單")
        assertEquals(1, tasks.size)
        assertEquals("寄出報價", tasks[0].title)
        assertEquals("工作清單", tasks[0].listName)
        assertEquals("high", tasks[0].importance)
    }

    @Test
    fun `a completed task is dropped even though the server was asked to filter it`() {
        // A server-side filter is a request, not a guarantee.
        val body = """{"value":[{"title":"done","status":"completed"}]}"""
        assertTrue(GraphModels.tasks(body, "L").isEmpty())
    }

    @Test
    fun `a task list with no name still gets one`() {
        val body = """{"value":[{"id":"abc","displayName":""},{"displayName":"沒有 id"}]}"""
        val lists = GraphModels.taskLists(body)
        assertEquals(1, lists.size)
        assertEquals("abc", lists[0].first)
        assertEquals("工作", lists[0].second)
    }

    @Test
    fun `timeOf reads a graph timestamp and refuses anything else`() {
        assertEquals("09:30", GraphModels.timeOf("2026-08-23T09:30:00.0000000"))
        assertNull(GraphModels.timeOf("2026-08-23"))
        assertNull(GraphModels.timeOf(""))
        assertNull(GraphModels.timeOf(null))
        assertNull(GraphModels.timeOf("garbage"))
    }
}
