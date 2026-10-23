package io.github.peanutpersimmon.rido.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rido.ui.components.CompactDateNavigation
import io.github.peanutpersimmon.rido.ui.components.TimelinePointItem
import io.github.peanutpersimmon.rido.ui.feature.editor.components.ReminderBell
import io.github.peanutpersimmon.rido.ui.theme.RidoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ReminderAndDateControlsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun bellStateUsesActiveAndInactiveIcons() {
        compose.setContent {
            RidoTheme {
                Column {
                    ReminderBell("计划提醒", null) {}
                    ReminderBell("截止提醒", 10) {}
                }
            }
        }
        compose.onNodeWithTag("reminder-bell-计划提醒-default").assertExists()
        compose.onNodeWithTag("reminder-bell-截止提醒-custom").assertExists()
        compose.onNodeWithTag("reminder-bell-计划提醒-custom").assertDoesNotExist()
    }

    @Test fun arrowTargetsAreWideAndCenterWhitespaceDoesNotOpenDate() {
        var previous = 0
        var opened = 0
        compose.setContent {
            RidoTheme {
                CompactDateNavigation("10/5", { previous++ }, {}, { opened++ },
                    Modifier.width(320.dp), touchTarget = 44.dp)
            }
        }
        val arrow = compose.onNodeWithTag("date-navigation-previous").fetchSemanticsNode().boundsInRoot
        val center = compose.onNodeWithTag("date-navigation-center").fetchSemanticsNode().boundsInRoot
        val label = compose.onNodeWithTag("date-navigation-label").fetchSemanticsNode().boundsInRoot
        assertTrue(arrow.width >= 40f * compose.density.density)
        assertTrue(center.width > label.width)
        compose.onNodeWithTag("date-navigation-center").performTouchInput {
            click(Offset(4f, center.height / 2f))
        }
        compose.runOnIdle { assertEquals(0, opened) }
        compose.onNodeWithTag("date-navigation-previous").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, previous) }
    }

    @Test fun overdueMarkerPrecedesTitleButCompletedTaskHasNoMarker() {
        compose.setContent {
            RidoTheme {
                Column {
                    TimelinePointItem("看文献", null, emptyList(), 1f, {},
                        Modifier.width(220.dp), overdue = true, prioritizeTitle = true)
                    TimelinePointItem("看书", null, emptyList(), 1f, {},
                        Modifier.width(220.dp), overdue = true, completed = true,
                        prioritizeTitle = true)
                }
            }
        }
        compose.onNodeWithText("! 看文献").assertExists()
        compose.onNodeWithText("看书完成").assertExists()
        compose.onNodeWithText("! 看书完成").assertDoesNotExist()
    }
}
