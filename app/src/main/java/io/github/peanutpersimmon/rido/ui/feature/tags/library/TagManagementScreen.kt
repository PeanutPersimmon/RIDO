package io.github.peanutpersimmon.rido.ui.feature.tags.library

// Ported from MyLibrary/ui/settings/TagManagementScreen.kt; RIDO data/theme adapters live in LibraryTagAdapters.
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.peanutpersimmon.rido.ui.components.CurrentRootItemRow
import io.github.peanutpersimmon.rido.ui.components.MetadataAction
import io.github.peanutpersimmon.rido.ui.components.MetadataActionDialog
import io.github.peanutpersimmon.rido.ui.components.RootChipRow
import io.github.peanutpersimmon.rido.ui.theme.AppTheme
import androidx.compose.material3.MaterialTheme

@Composable
fun TagManagementScreen(
    state: TagManagementUiState,
    onSelectRoot: (Long) -> Unit,
    onCreateRoot: (String, onDone: () -> Unit) -> Unit,
    onCreateChildren: (Long, List<String>, onDone: () -> Unit) -> Unit,
    onRename: (Long, String, onDone: () -> Unit) -> Unit,
    onDelete: (Long) -> Unit,
    onReorderRoots: (List<Long>) -> Unit,
    onReorderChildren: (Long, List<Long>) -> Unit,
    onClearError: () -> Unit,
    onInputCancelChange: ((() -> Unit)?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showCreateRoot by rememberSaveable { mutableStateOf(false) }
    var addingChildrenToId by rememberSaveable { mutableStateOf<Long?>(null) }
    var actionTargetId by rememberSaveable { mutableStateOf<Long?>(null) }
    var renamingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var deletingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val selectedRoot = state.selectedRoot
    val focusManager = LocalFocusManager.current
    fun cancelInput() {
        showCreateRoot = false
        addingChildrenToId = null
        renamingId = null
        focusManager.clearFocus()
        onClearError()
    }
    val inputOpen = showCreateRoot || addingChildrenToId != null || renamingId != null
    BackHandler(inputOpen) { cancelInput() }
    DisposableEffect(inputOpen) {
        onInputCancelChange(if (inputOpen) ::cancelInput else null)
        onDispose { onInputCancelChange(null) }
    }
    LaunchedEffect(state.tags) {
        if (renamingId != null && state.tags.none { it.id == renamingId && it.enabled }) cancelInput()
        if (addingChildrenToId != null && state.tags.none { it.id == addingChildrenToId && it.enabled }) cancelInput()
    }

    AppScreenContainer(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
            .verticalScroll(rememberScrollState()).clickable {
            if (inputOpen) cancelInput()
        }, verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs)) {

        state.errorMessage?.let { message ->
            Text(
                text = message,
                modifier = Modifier.padding(
                    start = ScreenHorizontalPadding,
                    end = ScreenHorizontalPadding,
                    bottom = 6.dp
                ),
                style = LibraryAppearance.typography.metadata,
                color = LibraryAppearance.colors.mutedText
            )
        }

        RootChipRow(
            items = state.rootTags,
            id = { it.id },
            name = { it.name },
            selectedId = selectedRoot?.id,
            errorMessage = state.errorMessage,
            adding = showCreateRoot,
            renamingId = renamingId,
            onSelect = { cancelInput(); onSelectRoot(it) },
            onAdd = { cancelInput(); showCreateRoot = true },
            onCreate = { name ->
                onCreateRoot(name) {
                    showCreateRoot = false
                    focusManager.clearFocus()
                }
            },
            onRename = { id, name ->
                onRename(id, name) {
                    renamingId = null
                    focusManager.clearFocus()
                }
            },
            onCancelInput = ::cancelInput,
            onDraftChange = onClearError,
            validateNew = { draft ->
                if (state.rootTags.any { it.name.equals(draft, ignoreCase = true) })
                    "同级标签名称已存在" else null
            },
            validateRename = { tag, draft ->
                if (state.rootTags.any { it.id != tag.id && it.name.equals(draft, ignoreCase = true) })
                    "同级标签名称已存在" else null
            },
            onReorder = onReorderRoots
        )

        if (selectedRoot == null) {
            Text("暂无标签", style = AppTheme.type.caption,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val children = state.selectedChildren
            CurrentRootItemRow(
                name = selectedRoot.name,
                detail = "二级标签 ${children.size} 个",
                onRename = {
                    cancelInput()
                    renamingId = selectedRoot.id
                },
                actionIcon = Icons.Outlined.Delete,
                actionDescription = "删除",
                onAction = {
                    cancelInput()
                    deletingId = selectedRoot.id
                },
            )
            ReorderableChildList(
                parentId = selectedRoot.id,
                children = children,
                errorMessage = state.errorMessage,
                adding = addingChildrenToId == selectedRoot.id,
                renamingId = renamingId,
                onMore = {
                    cancelInput()
                    actionTargetId = it.id
                },
                onAdd = {
                    cancelInput()
                    addingChildrenToId = selectedRoot.id
                },
                onCreate = { name ->
                    onCreateChildren(selectedRoot.id, listOf(name)) {
                        addingChildrenToId = null
                        focusManager.clearFocus()
                    }
                },
                onRename = { id, name ->
                    onRename(id, name) {
                        renamingId = null
                        focusManager.clearFocus()
                    }
                },
                onCancelInput = ::cancelInput,
                onDraftChange = onClearError,
                onReorder = { onReorderChildren(selectedRoot.id, it) },
                modifier = Modifier.fillMaxWidth()
            )
        }
        }
    }

    state.tags.firstOrNull { it.id == actionTargetId && it.parentId != null }?.let { tag ->
        MetadataActionDialog(
            title = tag.name,
            actions = listOf(
                MetadataAction("重命名") {
                    actionTargetId = null
                    cancelInput()
                    renamingId = tag.id
                },
                MetadataAction("删除") {
                    actionTargetId = null
                    cancelInput()
                    deletingId = tag.id
                },
            ),
            onDismiss = { actionTargetId = null }
        )
    }

    state.tags.firstOrNull { it.id == deletingId }?.let { tag ->
        val childCount = if (tag.parentId == null) state.tags.count {
            it.enabled && it.parentId == tag.id
        } else 0
        val usageCount = state.usageCounts[tag.id]
        AppConfirmDialog(
            title = deleteDialogTitle(tag, usageCount, childCount),
            message = deleteDialogMessage(tag),
            confirmText = "删除",
            dismissText = "取消",
            destructive = true,
            onConfirm = {
                onDelete(tag.id)
                deletingId = null
            },
            onDismiss = { deletingId = null }
        )
    }
}


