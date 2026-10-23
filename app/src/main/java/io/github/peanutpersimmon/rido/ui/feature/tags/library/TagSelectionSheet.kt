package io.github.peanutpersimmon.rido.ui.feature.tags.library

// Ported from MyLibrary/ui/components/TagSelectionSheet.kt; RIDO data/theme adapters live in LibraryTagAdapters.
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import io.github.peanutpersimmon.rido.ui.feature.tags.contextualTagSearch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Check
import io.github.peanutpersimmon.rido.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rido.ui.components.CompactChip
import io.github.peanutpersimmon.rido.ui.feature.editor.components.EditorIconAction
import io.github.peanutpersimmon.rido.ui.theme.AppTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagSelectionSheet(
    tags: List<LibraryTag>,
    selectedIds: Set<Long>,
    onDone: (Set<Long>) -> Unit,
    onCreateTag: (String, Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val roots = tags.filter { it.parentId == null }
    var query by remember { mutableStateOf("") }
    var temporarySelection by remember { mutableStateOf(selectedIds) }
    var activeRootId by remember { mutableStateOf<Long?>(roots.firstOrNull()?.id) }
    var pendingCreation by remember { mutableStateOf<PendingTagCreation?>(null) }
    val colors = LibraryAppearance.colors

    LaunchedEffect(roots) {
        if (activeRootId == null || roots.none { it.id == activeRootId }) {
            activeRootId = roots.firstOrNull()?.id
        }
    }
    LaunchedEffect(tags, pendingCreation) {
        val pending = pendingCreation ?: return@LaunchedEffect
        val created = tags.firstOrNull {
            it.parentId == pending.parentId &&
                it.name.equals(pending.name, ignoreCase = true)
        } ?: return@LaunchedEffect
        temporarySelection = temporarySelection + created.id
        pendingCreation = null
    }

    val activeRoot = roots.firstOrNull { it.id == activeRootId }
    val searchResult = contextualTagSearch(
        tags = tags,
        activeRootId = activeRootId,
        query = query,
        allowCreation = activeRoot?.enabled == true
    )
    val hasChipContent = searchResult.visibleTags.isNotEmpty() || searchResult.canCreate

    AppBottomSheet(
        title = "选择标签",
        bottomVisual = if (roots.isEmpty())
            io.github.peanutpersimmon.rido.ui.components.SheetBottomVisual.Surface
        else io.github.peanutpersimmon.rido.ui.components.SheetBottomVisual.ChipSlot,
        onDismissRequest = onDismiss,
        headerActions = {
            EditorIconAction(Icons.Outlined.Check, "完成选择标签", {
                onDone(temporarySelection)
                onDismiss()
            }, compactSheetAction = true)
            EditorIconAction(Icons.Outlined.Close, "关闭选择标签", onDismiss, compactSheetAction = true)
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS)
        ) {
            io.github.peanutpersimmon.rido.ui.feature.settings.LocalMetadataState.current.error?.let {
                Text(it, style = LibraryAppearance.typography.metadata, color = colors.mutedText)
            }
            LibrarySearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = "搜索或创建标签",
                modifier = Modifier.fillMaxWidth()
            )

            if (roots.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = AppTheme.dimens.sheetRowVisualHeight - AppTheme.dimens.spaceXs),
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.tagChipGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(roots, key = { it.id }) { root ->
                        Box(Modifier.padding(vertical = AppTheme.dimens.spaceXs / 2)) {
                            CompactChip(
                                label = root.name,
                                selected = root.id == activeRootId,
                                primary = true,
                                onClick = {
                                    activeRootId = root.id
                                    query = ""
                                }
                            )
                        }
                    }
                }
            }

            if (hasChipContent || roots.isEmpty()) Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS)
            ) {
                if (hasChipContent) FlowRow(
                    modifier = Modifier.padding(vertical = AppTheme.dimens.spaceXs / 2),
                    horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.tagChipGap),
                    verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.tagChipGap)
                ) {
                    searchResult.visibleTags.forEach { tag ->
                        CompactChip(
                            label = tag.name + if (!tag.enabled) "（已归档）" else "",
                            selected = tag.id in temporarySelection,
                            primary = false,
                            onClick = {
                                if (tag.enabled || tag.id in temporarySelection) temporarySelection = temporarySelection.toggle(tag.id)
                            }
                        )
                    }
                    if (searchResult.canCreate) {
                        CompactChip(
                            label = "＋${searchResult.normalizedQuery}",
                            selected = false,
                            primary = false,
                            onClick = {
                                val parentId = requireNotNull(activeRoot).id
                                pendingCreation = PendingTagCreation(
                                    name = searchResult.normalizedQuery,
                                    parentId = parentId
                                )
                                onCreateTag(searchResult.normalizedQuery, parentId)
                                query = ""
                            }
                        )
                    }
                }

                if (roots.isEmpty()) {
                    Text(
                        text = "暂无一级标签，请先在标签设置中创建",
                        style = LibraryAppearance.typography.metadata,
                        color = colors.mutedText
                    )
                }
            }
        }
    }
}

private data class PendingTagCreation(
    val name: String,
    val parentId: Long?
)

internal fun Set<Long>.toggle(id: Long): Set<Long> =
    if (id in this) this - id else this + id
