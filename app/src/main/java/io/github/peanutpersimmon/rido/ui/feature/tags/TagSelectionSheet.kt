package io.github.peanutpersimmon.rido.ui.feature.tags

import androidx.compose.runtime.*
import io.github.peanutpersimmon.rido.domain.model.Tag
import io.github.peanutpersimmon.rido.ui.feature.settings.*

@Composable
fun TagSelectionSheet(tags: List<Tag>, selectedIds: Set<Long>, onDone: (Set<Long>) -> Unit, onDismiss: () -> Unit) {
    val model = LocalMetadataViewModel.current
    LaunchedEffect(model) { model?.clearError() }
    val visible = tags.filter { !it.isArchived || it.id in selectedIds ||
        tags.any { child -> child.parentId == it.id && child.id in selectedIds } }
    io.github.peanutpersimmon.rido.ui.feature.tags.library.TagSelectionSheet(
        tags = visible, selectedIds = selectedIds, onDone = onDone,
        onCreateTag = { name, parent -> model?.createTags(parent, listOf(name)) },
        onDismiss = onDismiss,
    )
}
