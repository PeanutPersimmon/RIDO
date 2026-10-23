package io.github.peanutpersimmon.rido.ui.feature.tags.library

// Ported from MyLibrary/ui/settings/TagManagementScreen.kt; RIDO data/theme adapters live in LibraryTagAdapters.
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import io.github.peanutpersimmon.rido.ui.components.ReorderableList
import io.github.peanutpersimmon.rido.ui.components.CompactChip
import io.github.peanutpersimmon.rido.ui.components.InlineNameEditor
import io.github.peanutpersimmon.rido.ui.theme.AppTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReorderableChildList(
    parentId: Long,
    children: List<LibraryTag>,
    errorMessage: String?,
    adding: Boolean,
    renamingId: Long?,
    onMore: (LibraryTag) -> Unit,
    onAdd: () -> Unit,
    onCreate: (String) -> Unit,
    onRename: (Long, String) -> Unit,
    onCancelInput: () -> Unit,
    onDraftChange: () -> Unit,
    onReorder: (List<Long>) -> Unit,
    modifier: Modifier = Modifier
) {
    val localChildren = remember(parentId) { mutableStateListOf<LibraryTag>() }
    val bounds = remember(parentId) { mutableStateMapOf<Long, androidx.compose.ui.geometry.Rect>() }
    var draggingId by remember(parentId) { mutableStateOf<Long?>(null) }
    LaunchedEffect(children, errorMessage) {
        if (draggingId == null) {
            localChildren.clear()
            localChildren.addAll(children)
        }
    }
    fun finishDrag() {
        draggingId = null
        val ids = localChildren.map { it.id }
        if (ids != children.map { it.id }) onReorder(ids)
    }
    Column(modifier.testTag("child_tag_list")) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(vertical = AppTheme.dimens.spaceXs / 2),
            horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.tagChipGap),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.tagChipGap),
        ) {
            localChildren.forEach { child ->
                if (renamingId == child.id) {
                    androidx.compose.runtime.key(child.id) {
                        InlineNameEditor(
                            initialValue = child.name,
                            placeholder = "输入标签名称",
                            onSubmit = { onRename(child.id, it) },
                            onCancel = onCancelInput,
                            onDraftChange = onDraftChange,
                            validate = { draft ->
                                if (children.any { it.id != child.id && it.name.equals(draft, ignoreCase = true) })
                                    "同级标签名称已存在" else null
                            },
                            errorMessage = errorMessage,
                        )
                    }
                } else CompactChip(
                    label = child.name,
                    onClick = { if (draggingId == null) onMore(child) },
                    selected = draggingId == child.id,
                    modifier = Modifier
                        .testTag("child_tag_row_${child.id}")
                        .onGloballyPositioned { bounds[child.id] = it.boundsInRoot() }
                        .pointerInput(child.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { draggingId = child.id },
                                onDragCancel = ::finishDrag,
                                onDragEnd = ::finishDrag,
                                onDrag = { change, _ ->
                                    change.consume()
                                    val pointer = bounds[child.id]?.topLeft?.plus(change.position)
                                        ?: return@detectDragGesturesAfterLongPress
                                    val target = bounds.entries
                                        .filter { it.key != child.id && localChildren.any { tag -> tag.id == it.key } }
                                        .minByOrNull { (_, rect) -> (rect.center - pointer).getDistance() }
                                        ?: return@detectDragGesturesAfterLongPress
                                    val sourceIndex = localChildren.indexOfFirst { it.id == child.id }
                                    val targetIndex = localChildren.indexOfFirst { it.id == target.key }
                                    if (sourceIndex >= 0 && targetIndex >= 0 && target.value.contains(pointer)) {
                                        localChildren.add(targetIndex, localChildren.removeAt(sourceIndex))
                                    }
                                },
                            )
                        },
                )
            }
            if (adding) InlineNameEditor(
                initialValue = "",
                placeholder = "输入标签名称",
                onSubmit = onCreate,
                onCancel = onCancelInput,
                onDraftChange = onDraftChange,
                validate = { draft ->
                    if (children.any { it.name.equals(draft, ignoreCase = true) })
                        "同级标签名称已存在" else null
                },
                errorMessage = errorMessage,
            ) else CompactChip("+", onAdd, Modifier.testTag("add_child_tag_row"))
        }
    }
}

@Composable
internal fun ChildTagRow(
    tag: LibraryTag,
    isDragging: Boolean,
    onMore: () -> Unit,
    dragHandleModifier: Modifier,
) {
    val colors = LibraryAppearance.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TagTouchHeight)
            .testTag("child_tag_row_${tag.id}")
    ) {
        AppThemeSurface(
            role = SurfaceRole.CARD,
            modifier = Modifier
                .fillMaxWidth()
                .height(TagRowHeight)
                .align(Alignment.Center),
            border = if (isDragging) BorderStroke(1.dp, colors.accent) else null,
            shape = LibraryShapes.small,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Spacer(Modifier.width(TagTouchHeight))
                Text(
                    text = tag.name,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = LibraryAppearance.typography.body,
                    color = colors.textPrimary,
                )
                Spacer(Modifier.width(TagTouchHeight))
            }
        }
        Box(
            modifier = Modifier
                .size(TagTouchHeight)
                .align(Alignment.CenterStart)
                .then(dragHandleModifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.DragHandle,
                contentDescription = "拖动排序",
                modifier = Modifier.size(AppTheme.dimens.reorderHandleIconSize),
                tint = colors.textSecondary,
            )
        }
        Box(
            modifier = Modifier
                .size(TagTouchHeight)
                .align(Alignment.CenterEnd)
                .noRippleClickable(enabled = !isDragging, onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = "管理二级标签",
                modifier = Modifier.size(AppTheme.dimens.reorderHandleIconSize),
                tint = colors.textSecondary,
            )
        }
    }
}

@Composable
internal fun AddChildCard(onClick: () -> Unit) {
    // 行视觉体收紧为 tagRowHeight（<44dp），透明外壳承担 minimumTouchTarget 命中区。
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TagTouchHeight)
            .testTag("add_child_tag_row")
            .noRippleClickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AppThemeSurface(
            role = SurfaceRole.CARD,
            modifier = Modifier
                .fillMaxWidth()
                .height(TagRowHeight),
            shape = LibraryShapes.small,
            shadowElevation = 0.dp,
            tonalElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "添加二级标签",
                    modifier = Modifier.size(21.dp),
                    tint = LibraryAppearance.colors.textSecondary
                )
            }
        }
    }
}


