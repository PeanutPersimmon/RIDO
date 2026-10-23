package io.github.peanutpersimmon.rido.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import io.github.peanutpersimmon.rido.domain.model.Tag
import io.github.peanutpersimmon.rido.ui.components.ReorderableList
import io.github.peanutpersimmon.rido.ui.feature.tags.library.TagManagementScreen
import io.github.peanutpersimmon.rido.ui.feature.tags.library.TagManagementUiState
import io.github.peanutpersimmon.rido.ui.theme.AppTheme
import io.github.peanutpersimmon.rido.ui.theme.RidoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class TagManagementScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun noTagsOpensWithoutCrash() {
        setTagScreen(emptyList())

        compose.onNodeWithText("暂无标签").assertExists()
    }

    @Test fun rootWithoutChildrenComposesAddFooterWithoutCrash() {
        setTagScreen(listOf(tag(1, "工作")))

        compose.onNodeWithText("工作").assertExists()
        compose.onNodeWithTag("add_child_tag_row").assertExists()
    }

    @Test fun multipleChildrenCanScrollToAddFooter() {
        val root = tag(1, "工作")
        val children = (1L..40L).map { id ->
            tag(id + 1, "子标签$id", parentId = root.id, sortOrder = id.toInt())
        }
        setTagScreen(listOf(root) + children)

        compose.onNodeWithTag("add_child_tag_row").performScrollTo()
        compose.onNodeWithTag("add_child_tag_row").assertIsDisplayed()
    }

    @Test fun addFooterSurvivesSaveableStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            RidoTheme {
                TagManagementScreen(
                    state = TagManagementUiState(
                        tags = listOf(tag(1, "工作"), tag(2, "项目", parentId = 1)),
                        selectedRootId = 1,
                        isLoading = false,
                    ),
                    onSelectRoot = {},
                    onCreateRoot = { _, _ -> },
                    onCreateChildren = { _, _, _ -> },
                    onRename = { _, _, _ -> },
                    onDelete = {},
                    onReorderRoots = {},
                    onReorderChildren = { _, _ -> },
                    onClearError = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        compose.onNodeWithTag("add_child_tag_row").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("add_child_tag_row").assertExists()
    }

    @Test fun failedChildCreateKeepsInlineInputOpen() {
        compose.setContent {
            RidoTheme {
                var error by remember { mutableStateOf<String?>(null) }
                TagManagementScreen(
                    state = TagManagementUiState(
                        tags = listOf(tag(1, "工作")),
                        selectedRootId = 1,
                        isLoading = false,
                        errorMessage = error,
                    ),
                    onSelectRoot = {},
                    onCreateRoot = { _, _ -> },
                    onCreateChildren = { _, _, _ -> error = "写入失败" },
                    onRename = { _, _, _ -> },
                    onDelete = {},
                    onReorderRoots = {},
                    onReorderChildren = { _, _ -> },
                    onClearError = { error = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        compose.onNodeWithTag("add_child_tag_row").performClick()
        compose.onNodeWithContentDescription("输入标签名称").performTextInput("项目")
        compose.onNodeWithContentDescription("保存").performClick()

        compose.onNodeWithText("写入失败").assertExists()
        compose.onNodeWithContentDescription("输入标签名称").assertExists()
    }

    @Test fun reorderListWithoutFooterStillCommitsDragForPlanOrder() {
        var committed: List<String>? = null
        compose.setContent {
            RidoTheme {
                var order by remember { mutableStateOf(listOf("first", "second")) }
                Box(Modifier.fillMaxSize()) {
                    ReorderableList(
                        items = order,
                        key = { it },
                        onOrderCommitted = {
                            order = it
                            committed = it
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AppTheme.dimens.minimumTouchTarget * 3),
                        moveUpLabel = "上移视图",
                        moveDownLabel = "下移视图",
                    ) { item, _, _, dragHandleModifier ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(AppTheme.dimens.minimumTouchTarget)
                                .testTag("plan_order_row_$item"),
                        ) {
                            Text(item, Modifier.align(Alignment.CenterStart))
                            Box(
                                Modifier
                                    .size(AppTheme.dimens.minimumTouchTarget)
                                    .align(Alignment.CenterEnd)
                                    .then(dragHandleModifier)
                                    .testTag("plan_order_handle_$item"),
                            )
                        }
                    }
                }
            }
        }

        compose.onNodeWithTag("plan_order_handle_first").performTouchInput {
            swipe(
                start = center,
                end = center + Offset(0f, height * 2f),
                durationMillis = 500,
            )
        }
        compose.waitForIdle()
        assertEquals(listOf("second", "first"), committed)
    }

    private fun setTagScreen(tags: List<Tag>) {
        compose.setContent {
            RidoTheme {
                TagManagementScreen(
                    state = TagManagementUiState(
                        tags = tags,
                        isLoading = false,
                    ),
                    onSelectRoot = {},
                    onCreateRoot = { _, _ -> },
                    onCreateChildren = { _, _, _ -> },
                    onRename = { _, _, _ -> },
                    onDelete = {},
                    onReorderRoots = {},
                    onReorderChildren = { _, _ -> },
                    onClearError = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    private fun tag(
        id: Long,
        name: String,
        parentId: Long? = null,
        sortOrder: Int = 0,
    ) = Tag(
        id = id,
        name = name,
        sortOrder = sortOrder,
        createdAtMillis = 0,
        updatedAtMillis = 0,
        parentId = parentId,
    )
}
