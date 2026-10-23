package io.github.peanutpersimmon.rido.presentation.today

import io.github.peanutpersimmon.rido.domain.model.*
import io.github.peanutpersimmon.rido.domain.time.TimeInterval
import io.github.peanutpersimmon.rido.domain.usecase.CategoryPresentation
import io.github.peanutpersimmon.rido.domain.usecase.ClassificationLabels
import io.github.peanutpersimmon.rido.domain.usecase.TodayPlanProjector
import io.github.peanutpersimmon.rido.domain.usecase.TodayPlanSource
import io.github.peanutpersimmon.rido.domain.usecase.TimelineMetadataSources
import io.github.peanutpersimmon.rido.ui.feature.today.isPlan
import io.github.peanutpersimmon.rido.ui.feature.today.isTimedSpan
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class TodayUiMapperTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val date = LocalDate.of(2026, 9, 24)
    private val start = date.atStartOfDay(zone).toInstant().toEpochMilli()

    @Test fun timedOverdueTaskCountsInSummaryWithoutDuplicatingTimelineCard() {
        val task = Task(id = 12, title = "提交", plannedDateEpochDay = date.toEpochDay(),
            plannedStartMinuteOfDay = 600, dueDateEpochDay = date.toEpochDay(),
            dueMinuteOfDay = 720, createdAtMillis = start, updatedAtMillis = start)
        val now = start + 12 * 3_600_000L + 30 * 60_000L
        val window = AwakeDisplayWindow(date, TimeInterval(start, start + 86_400_000),
            null, null, BoundarySource.DEFAULT, BoundarySource.DEFAULT)
        val point = TaskPlanPoint(task.id, start + 10 * 3_600_000L, task.title, null, emptySet(), null)
        fun mapped(current: Task, time: Long = now): TodayUiState.Content = TodayUiMapper.map(
            date, window, listOf(point), null, ClassificationLabels(emptyMap(), emptyMap()),
            TodayPlanProjector.project(date, zone, time, listOf(current), emptyList()),
            TimelineDisplayMode.OVERVIEW, time, zone, Locale.CHINA,
            TimelineMetadataSources(mapOf(current.id to current), emptyMap()))
        val overdue = mapped(task)
        assertEquals(1, overdue.planSummary!!.overdueCount)
        assertEquals(0, overdue.planSummary!!.items.size)
        assertEquals(1, overdue.timelineEntries.count { it.entryType == TimelineEntryType.TASK_PLAN_POINT })
        assertEquals(0, mapped(task.copy(status = TaskStatus.DONE, completedAtMillis = now)).planSummary?.overdueCount ?: 0)
        assertEquals(0, mapped(task.copy(dueDateEpochDay = date.plusDays(1).toEpochDay())).planSummary?.overdueCount ?: 0)
        val dateOnly = task.copy(dueMinuteOfDay = null)
        assertEquals(0, mapped(dateOnly).planSummary?.overdueCount ?: 0)
        assertEquals(1, mapped(dateOnly, start + 86_400_000L + 1000).planSummary?.overdueCount ?: 0)
    }

    @Test fun dueOnlyTaskShowsDeadlineInExpandedSummaryItem() {
        val task = Task(id = 5, title = "交稿", dueDateEpochDay = date.toEpochDay(),
            dueMinuteOfDay = 10 * 60 + 50, createdAtMillis = start,
            updatedAtMillis = start)
        val now = start + 9 * 3_600_000L
        val plans = TodayPlanProjector.project(date, zone, now, listOf(task), emptyList())
        val window = AwakeDisplayWindow(date, TimeInterval(start, start + 86_400_000),
            null, null, BoundarySource.DEFAULT, BoundarySource.DEFAULT)
        val result = TodayUiMapper.map(date, window, emptyList(), null,
            ClassificationLabels(emptyMap(), emptyMap()), plans, TimelineDisplayMode.OVERVIEW,
            now, zone, Locale.CHINA, TimelineMetadataSources(mapOf(task.id to task), emptyMap()))
        assertEquals("10:50", result.planSummary!!.items.single().detail)
        assertTrue(result.timelineEntries.isEmpty())
    }

    @Test fun completedUntimedTaskDoesNotContributeToRemainingCount() {
        val active = Task(id = 1, title = "待办", plannedDateEpochDay = date.toEpochDay(),
            createdAtMillis = start, updatedAtMillis = start)
        val done = active.copy(id = 2, title = "已完成", status = TaskStatus.DONE,
            completedAtMillis = start + 3_600_000L)
        val now = start + 12 * 3_600_000L
        val window = AwakeDisplayWindow(date, TimeInterval(start, start + 86_400_000),
            null, null, BoundarySource.DEFAULT, BoundarySource.DEFAULT)
        val result = TodayUiMapper.map(date, window, emptyList(), null,
            ClassificationLabels(emptyMap(), emptyMap()),
            TodayPlanProjector.project(date, zone, now, listOf(active, done), emptyList()),
            TimelineDisplayMode.OVERVIEW, now, zone, Locale.CHINA)
        assertEquals(1, result.planSummary!!.remainingCount)
        assertEquals(2, result.planSummary!!.items.size)
    }

    @Test fun untimedTaskAndEventKeepCategoryIdentityThroughPlanSummary() {
        val task = Task(id = 1, title = "写论文", plannedDateEpochDay = date.toEpochDay(),
            categoryId = 11, createdAtMillis = start, updatedAtMillis = start)
        val unassigned = task.copy(id = 2, title = "未分类", categoryId = null)
        val event = TaggedEvent(Event(id = 3, title = "组会", dateEpochDay = date.toEpochDay(),
            categoryId = 12, createdAtMillis = start, updatedAtMillis = start))
        val plans = TodayPlanProjector.project(date, zone, start + 12 * 3_600_000L,
            listOf(task, unassigned), listOf(event))
        assertEquals(mapOf(1L to 11L, 2L to null, 3L to 12L),
            plans.associate { it.sourceId to it.categoryId })
        assertTrue(plans.all { it.startAtMillis == null })

        val labels = ClassificationLabels(mapOf(
            11L to CategoryPresentation("研究", "blue", false),
            12L to CategoryPresentation("会议", "neutral", false),
        ), emptyMap())
        val window = AwakeDisplayWindow(date, TimeInterval(start, start + 86_400_000),
            null, null, BoundarySource.DEFAULT, BoundarySource.DEFAULT)
        fun summary(current: ClassificationLabels) = TodayUiMapper.map(date, window,
            emptyList(), null, current, plans, TimelineDisplayMode.OVERVIEW,
            start + 12 * 3_600_000L, zone, Locale.CHINA).planSummary!!.items
        val items = summary(labels)
        assertEquals("blue", items.single { it.sourceId == 1L }.categoryColorKey)
        assertEquals(TodayPlanSource.TASK, items.single { it.sourceId == 1L }.source)
        assertNull(items.single { it.sourceId == 2L }.categoryColorKey)
        assertEquals("neutral", items.single { it.sourceId == 3L }.categoryColorKey)
        assertEquals(TodayPlanSource.EVENT, items.single { it.sourceId == 3L }.source)
        assertEquals("orange", summary(labels.copy(categoriesById = labels.categoriesById +
            (11L to CategoryPresentation("研究", "orange", false))))
            .single { it.sourceId == 1L }.categoryColorKey)
    }

    @Test fun dateLabelDerivesEnglishWeekdayFromActualDate() {
        assertEquals("20\nSEP · SUN", TodayUiMapper.formatDateLabel(LocalDate.of(2026, 9, 20)))
        assertEquals("10\nSEP · THU", TodayUiMapper.formatDateLabel(LocalDate.of(2026, 9, 10)))
    }

    @Test fun sessionPointMapsToActualSideWithoutDurationOrActiveState() {
        val time = start + 17 * 3_600_000 + 26 * 60_000
        val point = SessionPoint(42, time, "取快递", null, emptySet(), null, null)
        val active = TaggedSession(Session(id = 42, title = "取快递",
            temporalType = SessionTemporalType.POINT, startAtMillis = time,
            endAtMillis = null, createdAtMillis = time, updatedAtMillis = time))
        val window = AwakeDisplayWindow(date, TimeInterval(start, start + 86_400_000),
            null, null, BoundarySource.DEFAULT, BoundarySource.DEFAULT)

        val entry = TodayUiMapper.map(date, window, listOf(point), active,
            ClassificationLabels(emptyMap(), emptyMap()), emptyList(),
            TimelineDisplayMode.OVERVIEW, time, zone, Locale.CHINA).timelineEntries.single()

        assertEquals(TimelineEntryType.SESSION_POINT, entry.entryType)
        assertEquals(42L, entry.sourceId)
        assertEquals("17:26", entry.timeLabel)
        assertEquals(listOf(time), entry.axisTimes)
        assertNull(entry.endAtMillis)
        assertNull(entry.durationLabel)
        assertFalse(entry.isActive)
        assertFalse(entry.isPlan)
        assertFalse(entry.isTimedSpan)
    }
}
