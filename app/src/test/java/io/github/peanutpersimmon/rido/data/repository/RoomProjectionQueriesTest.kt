package io.github.peanutpersimmon.rido.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomProjectionQueriesTest {
    @Test
    fun dataChangeObserverTracksOnlyTodayAndPlanTables() {
        assertEquals(
            setOf(
                "sessions",
                "events",
                "tasks",
                "categories",
                "tags",
                "session_tag_cross_refs",
                "event_tag_cross_refs",
                "task_tag_cross_refs",
            ),
            PROJECTION_DATA_CHANGE_TABLES.toSet(),
        )
        assertEquals(8, PROJECTION_DATA_CHANGE_TABLES.size)
    }
}
