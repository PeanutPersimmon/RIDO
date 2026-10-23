package io.github.peanutpersimmon.rido.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rido.ui.feature.editor.components.*
import io.github.peanutpersimmon.rido.ui.components.picker.rememberPickerHostState
import io.github.peanutpersimmon.rido.ui.theme.RidoTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class EditorMetadataTest {
    @get:Rule val compose = createComposeRule()
    @Test fun unsetMetadataIsIconOnlyAndSetValueIsVisible() {
        val value = mutableStateOf<String?>(null)
        compose.setContent {
            RidoTheme { EditorTool(Icons.Outlined.FolderOpen, "分类", value.value, { value.value = "自定义分类" }) }
        }
        compose.onNodeWithText("分类").assertDoesNotExist()
        compose.onNodeWithContentDescription("分类").performClick()
        compose.onNodeWithText("自定义分类", useUnmergedTree = true).assertExists()
    }
    @Test fun dateAndTimeIsOneTextLineAt320dp() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.3f)) {
                RidoTheme {
                    Column(Modifier.width(320.dp).padding(horizontal = 16.dp)) {
                        EditorTimeModule {
                            DateTimeField("开始", EditorTimeRole.START, LocalDate.of(2026, 9, 21), LocalTime.of(6, 6), {})
                            DateTimeField("结束", EditorTimeRole.END, LocalDate.of(2026, 9, 21), LocalTime.of(8, 20), {})
                        }
                    }
                }
            }
        }
        listOf("9/21  06:06", "9/21  08:20").forEach { text ->
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            assertEquals(1, results.single().lineCount)
            assertFalse(results.single().hasVisualOverflow)
        }
    }

    @Test fun taskTimeAreaHasThreeIconFirstEntriesAt320dp() {
        compose.setContent {
            RidoTheme {
                val picker = rememberPickerHostState()
                Column(Modifier.width(320.dp).padding(horizontal = 16.dp)) {
                    TaskTimeLayout(planned = { modifier ->
                        TaskPlannedTimeField(
                            TaskPlannedTimeValue(LocalDate.of(2026, 9, 21),
                                LocalTime.of(9, 0)),
                            picker, modifier, onChange = {},
                        )
                    }, deadline = {
                        DateTimeField("截止", EditorTimeRole.END, LocalDate.of(2026, 9, 22), LocalTime.of(18, 0), {})
                    }, estimate = { EstimatedDurationField("2", "30") { _, _ -> } })
                }
            }
        }
        listOf("开始", "结束", "计划时间", "截止", "预计").forEach { label ->
            compose.onNodeWithText(label, useUnmergedTree = true).assertDoesNotExist()
        }
        compose.onAllNodesWithTag("task-time-plan-icon", useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag(EditorTimeRole.END.iconTestTag(), useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag(EditorTimeRole.ESTIMATE.iconTestTag(), useUnmergedTree = true).assertCountEquals(1)
        val plan = compose.onNodeWithText("9/21 · 09:00–10:00", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val due = compose.onNodeWithContentDescription("截止，9/22 18:00").fetchSemanticsNode().boundsInRoot
        val estimate = compose.onNodeWithContentDescription("预计，2h30m").fetchSemanticsNode().boundsInRoot
        assertTrue(due.top > plan.top)
        assertTrue(estimate.top > due.top)
    }

    @Test fun taskDeadlineAndEventEndRenderTheSameStopRoleIcon() {
        compose.setContent {
            RidoTheme {
                Column {
                    DateTimeField(
                        "截止",
                        EditorTimeRole.END,
                        LocalDate.of(2026, 9, 22),
                        LocalTime.of(18, 0),
                        {},
                    )
                    DateTimeField(
                        "日程结束",
                        EditorTimeRole.END,
                        LocalDate.of(2026, 9, 22),
                        LocalTime.of(19, 0),
                        {},
                    )
                }
            }
        }
        assertEquals(Icons.Outlined.Stop, editorTimeIcon(EditorTimeRole.END))
        compose.onAllNodesWithTag(EditorTimeRole.END.iconTestTag(), useUnmergedTree = true)
            .assertCountEquals(2)
    }
}
