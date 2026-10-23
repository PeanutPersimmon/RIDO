package io.github.peanutpersimmon.rido.ui.feature.tags

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import io.github.peanutpersimmon.rido.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rido.ui.feature.editor.components.EditorIconAction
import io.github.peanutpersimmon.rido.ui.feature.settings.*
import io.github.peanutpersimmon.rido.ui.feature.tags.library.TagManagementScreen
import io.github.peanutpersimmon.rido.ui.feature.tags.library.TagManagementUiState

/** Only adapts RIDO persistence and sheet hosting; content is the MyLibrary screen. */
@Composable
fun TagManagementSheet(onDismiss: () -> Unit) {
    val model = LocalMetadataViewModel.current ?: return
    val metadata = LocalMetadataState.current
    var selectedRoot by remember { mutableStateOf<Long?>(null) }
    var cancelInput by remember { mutableStateOf<(() -> Unit)?>(null) }
    val close = { cancelInput?.invoke() ?: onDismiss() }
    AppBottomSheet("标签", close,
        bottomVisual = if (metadata.tags.any { !it.isArchived && it.parentId == null })
            io.github.peanutpersimmon.rido.ui.components.SheetBottomVisual.ChipSlot
        else io.github.peanutpersimmon.rido.ui.components.SheetBottomVisual.Surface,
        headerAction = { EditorIconAction(Icons.Outlined.Close, "关闭标签", close, compactSheetAction = true) },
    ) {
        TagManagementScreen(
            state = TagManagementUiState(tags = metadata.tags, selectedRootId = selectedRoot,
                isLoading = false, errorMessage = metadata.error),
            onSelectRoot = { selectedRoot = it },
            onCreateRoot = { name, onDone ->
                model.createTags(null, listOf(name)) { ids ->
                    selectedRoot = ids.firstOrNull()
                    onDone()
                }
            },
            onCreateChildren = { parent, names, onDone ->
                model.createTags(parent, names) { onDone() }
            },
            onRename = { id, name, onDone -> model.renameTag(id, name, onDone) },
            onDelete = { model.deleteTag(it) },
            onReorderRoots = { model.reorderTags(null, it) },
            onReorderChildren = { parent, ids -> model.reorderTags(parent, ids) },
            onClearError = model::clearError,
            onInputCancelChange = { cancelInput = it },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
