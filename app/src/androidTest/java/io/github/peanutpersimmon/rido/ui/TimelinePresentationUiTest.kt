package io.github.peanutpersimmon.rido.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rido.domain.model.Category
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import io.github.peanutpersimmon.rido.presentation.plan.PlanUiState
import io.github.peanutpersimmon.rido.ui.components.TimelinePointItem
import io.github.peanutpersimmon.rido.ui.components.TimelinePointSide
import io.github.peanutpersimmon.rido.ui.feature.categories.ArchivedCategorySection
import io.github.peanutpersimmon.rido.ui.feature.plan.PlanTaskList
import io.github.peanutpersimmon.rido.ui.feature.plan.TaskListFilter
import io.github.peanutpersimmon.rido.ui.theme.RidoTheme
import io.github.peanutpersimmon.rido.ui.theme.LightSemanticColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimelinePresentationUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun taskListDefaultsToUnfinishedAndDoneTaskCanOpenAndReopen() {
        var opened: Long? = null
        var toggled: Long? = null
        compose.setContent {
            val filter = remember { mutableStateOf(TaskListFilter.UNFINISHED) }
            RidoTheme {
                Column {
                    Button(onClick = { filter.value = TaskListFilter.DONE }) { Text("已完成") }
                    PlanTaskList(PlanUiState(tasks = listOf(
                        Task(id = 1, title = "未完成任务", status = TaskStatus.IN_PROGRESS,
                            createdAtMillis = 0, updatedAtMillis = 0),
                        Task(id = 2, title = "已完成任务", status = TaskStatus.DONE,
                            completedAtMillis = 1000, createdAtMillis = 0, updatedAtMillis = 1000),
                    ), loading = false, nowMillis = 2000),
                        { opened = it }, { toggled = it.id }, filter.value)
                }
            }
        }
        compose.onNodeWithText("未完成任务").assertExists()
        compose.onNodeWithText("已完成任务").assertDoesNotExist()
        compose.onNodeWithText("已完成").performClick()
        compose.onNodeWithText("未完成任务").assertDoesNotExist()
        compose.onNodeWithText("已完成任务").assertExists().performClick()
        compose.onNodeWithContentDescription("取消完成").performClick()
        compose.runOnIdle {
            assertEquals(2L, opened)
            assertEquals(2L, toggled)
        }
    }

    @Test fun todayDueMetadataIsVisibleAtOverviewWithoutExpandingOtherDetails() {
        compose.setContent {
            RidoTheme {
                TimelinePointItem("看文献", "预计1h", listOf("远期截止"), 1f, {},
                    Modifier.width(220.dp), side = TimelinePointSide.PLANNED,
                    showMarker = false, alwaysVisibleMetadata = listOf("10:50"),
                    metadataProgress = 0f)
            }
        }
        compose.onNodeWithText("看文献").assertExists()
        compose.onNodeWithText("预计1h").assertExists()
        compose.onNodeWithText("10:50").assertExists()
        compose.onNodeWithText("远期截止").assertDoesNotExist()
    }

    @Test fun categoryPointMarkersFollowSideAndKeepClickTargets() {
        var clicked = 0
        compose.setContent {
            RidoTheme {
                Column {
                    TimelinePointItem("计划点", null, emptyList(), 1f, { clicked++ },
                        Modifier.width(220.dp).testTag("planned_point"),
                        side = TimelinePointSide.PLANNED, markerColor = Color.Red)
                    TimelinePointItem("事实点", null, emptyList(), 1f, { clicked++ },
                        Modifier.width(220.dp).testTag("actual_point"),
                        side = TimelinePointSide.ACTUAL, markerColor = Color.Blue)
                    TimelinePointItem("无分类", null, emptyList(), 1f, { clicked++ },
                        Modifier.width(220.dp).testTag("unassigned_point"),
                        side = TimelinePointSide.ACTUAL, showMarker = false)
                }
            }
        }
        val markers = compose.onAllNodesWithTag("timeline_point_category_marker", useUnmergedTree = true)
            .fetchSemanticsNodes()
        assertEquals(2, markers.size)
        val planMarker = markers[0].boundsInRoot
        val actualMarker = markers[1].boundsInRoot
        assertTrue(planMarker.left > actualMarker.left)
        assertEquals(planMarker.width, planMarker.height, 0.1f)
        assertEquals(actualMarker.width, actualMarker.height, 0.1f)
        compose.onNodeWithTag("planned_point").performClick()
        compose.onNodeWithTag("actual_point").performClick()
        compose.onNodeWithTag("unassigned_point").performClick()
        compose.runOnIdle { assertEquals(3, clicked) }
    }

    @Test fun overdueExclamationUsesRedSemanticSpanOnBothTimelineSides() {
        compose.setContent {
            RidoTheme {
                Column {
                    TimelinePointItem("今日任务", null, emptyList(), 1f, {},
                        Modifier.width(220.dp).testTag("today_overdue"),
                        overdue = true, side = TimelinePointSide.PLANNED)
                    TimelinePointItem("计划任务", null, emptyList(), 1f, {},
                        Modifier.width(220.dp).testTag("plan_overdue"),
                        overdue = true, side = TimelinePointSide.PLANNED)
                    TimelinePointItem("已完成", null, emptyList(), 1f, {},
                        Modifier.width(220.dp).testTag("completed_task"),
                        overdue = true, completed = true)
                }
            }
        }
        listOf("today_overdue", "plan_overdue").forEach { tag ->
            val text = compose.onNodeWithTag(tag).fetchSemanticsNode().config[SemanticsProperties.Text].single()
            assertTrue(text.text.startsWith("! "))
            val marker = text.spanStyles.single { it.start == 0 && it.end == 2 }
            assertEquals(LightSemanticColors.overdueMarker, marker.item.color)
            assertTrue(text.spanStyles.none { it.start >= 2 && it.item.color == LightSemanticColors.overdueMarker })
        }
        val done = compose.onNodeWithTag("completed_task").fetchSemanticsNode().config[SemanticsProperties.Text].single()
        assertTrue(!done.text.startsWith("!"))
    }

    @Test fun archivedCategoriesStartCollapsedAndRestoreLeavesArchiveSection() {
        compose.setContent {
            val categories = remember {
                mutableStateListOf(
                    Category(1, "工作", createdAtMillis = 0, updatedAtMillis = 0),
                    Category(2, "旧分类", isArchived = true, createdAtMillis = 0, updatedAtMillis = 0),
                )
            }
            RidoTheme {
                Column {
                    categories.filterNot(Category::isArchived).forEach { Text("活跃：${it.name}") }
                    ArchivedCategorySection(categories.filter(Category::isArchived), {}) { archived ->
                        Text("归档：${archived.name}")
                        Button(onClick = {
                            val index = categories.indexOfFirst { it.id == archived.id }
                            categories[index] = archived.copy(isArchived = false)
                        }) { Text("恢复") }
                    }
                }
            }
        }
        compose.onNodeWithText("活跃：工作").assertExists()
        compose.onNodeWithText("已归档分类 1", substring = true).assertExists()
        compose.onNodeWithText("归档：旧分类").assertDoesNotExist()
        compose.onNodeWithText("已归档分类 1", substring = true).performClick()
        compose.onNodeWithText("归档：旧分类").assertExists()
        compose.onNodeWithText("恢复").performClick()
        compose.onNodeWithText("活跃：旧分类").assertExists()
        compose.onNodeWithText("已归档分类 1", substring = true).assertDoesNotExist()
    }
}
