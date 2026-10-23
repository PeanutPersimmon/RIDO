package io.github.peanutpersimmon.rido.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.peanutpersimmon.rido.domain.model.*
import io.github.peanutpersimmon.rido.presentation.plan.PlanUiState
import io.github.peanutpersimmon.rido.presentation.plan.MonthContent
import io.github.peanutpersimmon.rido.presentation.today.*
import io.github.peanutpersimmon.rido.ui.components.TimelineZoomState
import io.github.peanutpersimmon.rido.ui.feature.plan.RecordDay
import io.github.peanutpersimmon.rido.ui.feature.plan.RecordWeek
import io.github.peanutpersimmon.rido.ui.feature.plan.PlanMonth
import io.github.peanutpersimmon.rido.ui.feature.plan.TodoDoneDay
import io.github.peanutpersimmon.rido.ui.feature.today.TodayTimeline
import io.github.peanutpersimmon.rido.ui.theme.RidoTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CompletedFactUiTest {
    @get:Rule val compose = createComposeRule()
    private val day = LocalDate.of(2026, 9, 22)
    private val zone = ZoneId.systemDefault()
    private val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
    private val at = day.atTime(10, 36).atZone(zone).toInstant().toEpochMilli()
    private val task = Task(id = 42, title = "拿快递", status = TaskStatus.DONE,
        completedAtMillis = at, createdAtMillis = at, updatedAtMillis = at)

    @Test fun monthPointSummaryOpensRecordWithoutDuration() {
        var opened: Long? = null
        var dateOpened = false
        val point = Session(id = 87, title = "取快递", temporalType = SessionTemporalType.POINT,
            startAtMillis = at, createdAtMillis = at, updatedAtMillis = at)
        compose.setContent {
            RidoTheme {
                PlanMonth(PlanUiState(selectedDate = day, monthContent = MonthContent.RECORD,
                    records = listOf(point), loading = false), {}, { dateOpened = true },
                    { opened = it }, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithText("取快递").assertExists().performClick()
        compose.onNodeWithText("0m").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(87L, opened)
            assertEquals(false, dateOpened)
        }
    }

    @Test fun dayWithOnlyCompletionDoesNotShowTask() = planPoint("day")
    @Test fun weekWithOnlyCompletionDoesNotShowTask() = planPoint("week")
    @Test fun todoDoneWithOnlyCompletionShowsFactAndOpensTask() = planPoint("todoDone")

    private fun planPoint(mode: String) {
        var opened: Long? = null
        compose.setContent {
            RidoTheme {
                when (mode) {
                    "day" -> RecordDay(day, emptyList(), emptyMap(), { _, _ -> }, TimelineZoomState(), Modifier.fillMaxSize())
                    "week" -> RecordWeek(day, emptyList(), emptyMap(), { _, _ -> }, TimelineZoomState(), Modifier.fillMaxSize())
                    else -> TodoDoneDay(PlanUiState(selectedDate = day, tasks = listOf(task),
                        loading = false, nowMillis = at,
                        awakeDisplayWindow = AwakeDisplayWindow(
                            anchorDate = day,
                            displayInterval = io.github.peanutpersimmon.rido.domain.time.TimeInterval(
                                start,
                                day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                            ),
                            actualWakeAtMillis = null,
                            actualSleepAtMillis = null,
                            startSource = BoundarySource.DEFAULT,
                            endSource = BoundarySource.DEFAULT,
                        )), { opened = it }, {}, { _, _ -> }, {},
                        TimelineZoomState(), Modifier.fillMaxSize())
                }
            }
        }
        if (mode == "todoDone") {
            compose.onNodeWithText("拿快递完成").assertExists().performClick()
            compose.onNodeWithText("暂无记录").assertDoesNotExist()
            compose.runOnIdle { assertEquals(42L, opened) }
        } else {
            compose.onNodeWithText("10:36 ● 拿快递").assertDoesNotExist()
            compose.runOnIdle { assertEquals(null, opened) }
        }
    }

    @Test fun todayPointRecordShowsTitleAndOpensSessionWithoutDuration() {
        var opened: Long? = null
        val entry = TimelineEntryUiModel(42, at, null, "拿快递", "10:36", null,
            TimelineEntryType.SESSION_POINT, TimelineState.ACTUAL,
            null, emptyList(), null, null, false)
        compose.setContent {
            RidoTheme {
                TodayTimeline(AwakeDayUiModel(start, start + 86_400_000, "00:00", "24:00",
                    wakeAtMillis = start, sleepAtMillis = start + 86_400_000),
                    listOf(entry), TimelineDisplayMode.OVERVIEW, at,
                    { opened = it.sourceId }, Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithText("拿快递").assertExists().performClick()
        compose.onNodeWithText("0m").assertDoesNotExist()
        compose.runOnIdle { assertEquals(42L, opened) }
    }

    @Test fun todoDonePointRecordShowsTitleAndOpensExistingRecordWithoutDuration() {
        val point = Session(id = 77, title = "取快递", kind = SessionKind.ACTIVITY,
            temporalType = SessionTemporalType.POINT, startAtMillis = at,
            endAtMillis = null, createdAtMillis = at, updatedAtMillis = at)
        var opened: Long? = null
        compose.setContent {
            RidoTheme {
                TodoDoneDay(PlanUiState(selectedDate = day, records = listOf(point),
                    loading = false, nowMillis = at,
                    awakeDisplayWindow = AwakeDisplayWindow(day,
                        io.github.peanutpersimmon.rido.domain.time.TimeInterval(
                            start, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()),
                        null, null, BoundarySource.DEFAULT, BoundarySource.DEFAULT)),
                    {}, {}, { id, _ -> opened = id }, {}, TimelineZoomState(), Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithText("取快递").assertExists().performClick()
        compose.onNodeWithText("0m").assertDoesNotExist()
        compose.runOnIdle { assertEquals(77L, opened) }
    }

    @Test fun todayPlannedAndActualPointsShowTextAndKeepClickRoutes() {
        val taskAt = day.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val eventAt = day.atTime(15, 0).atZone(zone).toInstant().toEpochMilli()
        val actualAt = day.atTime(16, 0).atZone(zone).toInstant().toEpochMilli()
        val intervalEnd = day.atTime(17, 0).atZone(zone).toInstant().toEpochMilli()
        val opened = mutableListOf<Long?>()
        fun entry(id: Long, atMillis: Long, end: Long?, title: String,
            type: TimelineEntryType, state: TimelineState) = TimelineEntryUiModel(
            id, atMillis, end, title, "", null, type, state,
            null, emptyList(), null, null, false)
        compose.setContent {
            RidoTheme {
                TodayTimeline(
                    AwakeDayUiModel(start, start + 86_400_000, "00:00", "24:00"),
                    listOf(
                        entry(1, taskAt, null, "写论文", TimelineEntryType.TASK_PLAN_POINT,
                            TimelineState.PLANNED).copy(estimatedDurationLabel = "2h", dueLabel = "截止 20:00"),
                        entry(2, eventAt, null, "交材料", TimelineEntryType.EVENT_BLOCK,
                            TimelineState.PLANNED),
                        entry(3, actualAt, null, "收到快递", TimelineEntryType.SESSION_POINT,
                            TimelineState.ACTUAL),
                        entry(4, actualAt, intervalEnd, "开组会", TimelineEntryType.EVENT_BLOCK,
                            TimelineState.PLANNED),
                        entry(5, taskAt, intervalEnd, "整理记录", TimelineEntryType.SESSION_BLOCK,
                            TimelineState.ACTUAL),
                        entry(6, start, null, "Wake", TimelineEntryType.WAKE_POINT,
                            TimelineState.BOUNDARY),
                        entry(7, start + 86_400_000, null, "Sleep", TimelineEntryType.SLEEP_POINT,
                            TimelineState.BOUNDARY),
                    ), TimelineDisplayMode.OVERVIEW, actualAt,
                    { opened += it.sourceId }, Modifier.fillMaxSize(),
                )
            }
        }
        compose.onNodeWithText("写论文").assertExists().performClick()
        compose.onNodeWithText("预计2h").assertExists()
        compose.onNodeWithText("交材料").assertExists().performClick()
        compose.onNodeWithText("收到快递").assertExists().performClick()
        compose.onNodeWithText("开组会").assertExists()
        compose.onNodeWithText("整理记录").assertExists()
        compose.onNodeWithText("Wake").assertDoesNotExist()
        compose.onNodeWithText("Sleep").assertDoesNotExist()
        compose.onNodeWithText("0m").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(1L, 2L, 3L), opened) }
    }

    @Test fun todoDonePlannedAndActualPointsShowTextAndKeepClickRoutes() {
        val taskAt = day.atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        val actualAt = day.atTime(16, 0).atZone(zone).toInstant().toEpochMilli()
        val plannedTask = Task(id = 51, title = "写论文", plannedDateEpochDay = day.toEpochDay(),
            plannedStartMinuteOfDay = 14 * 60, estimatedDurationMinutes = 120,
            createdAtMillis = start, updatedAtMillis = start)
        val plannedEvent = Event(id = 52, title = "交材料", dateEpochDay = day.toEpochDay(),
            startMinuteOfDay = 15 * 60, createdAtMillis = start, updatedAtMillis = start)
        val plannedPoint = Event(id = 53, title = "收到快递", dateEpochDay = day.toEpochDay(),
            startMinuteOfDay = 16 * 60,
            createdAtMillis = start, updatedAtMillis = start)
        val openedTasks = mutableListOf<Long>()
        val openedEvents = mutableListOf<Long>()
        compose.setContent {
            RidoTheme {
                TodoDoneDay(PlanUiState(selectedDate = day, tasks = listOf(plannedTask),
                    events = listOf(plannedEvent, plannedPoint), loading = false, nowMillis = actualAt,
                    awakeDisplayWindow = AwakeDisplayWindow(
                        anchorDate = day,
                        displayInterval = io.github.peanutpersimmon.rido.domain.time.TimeInterval(
                            start, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()),
                        actualWakeAtMillis = null, actualSleepAtMillis = null,
                        startSource = BoundarySource.DEFAULT, endSource = BoundarySource.DEFAULT,
                    )), { openedTasks += it }, { openedEvents += it }, { _, _ -> }, {},
                    TimelineZoomState(), Modifier.fillMaxSize())
            }
        }
        compose.onNodeWithText("写论文").assertExists().performClick()
        compose.onNodeWithText("预计 2h").assertExists()
        compose.onNodeWithText("交材料").assertExists().performClick()
        compose.onNodeWithText("收到快递").assertExists().performClick()
        compose.onNodeWithText("0m").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(51L), openedTasks)
            assertEquals(listOf(52L, 53L), openedEvents)
        }
    }
}
