package io.github.peanutpersimmon.rido.ui.feature.editor.components

import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.peanutpersimmon.rido.presentation.editor.EditorChoice
import io.github.peanutpersimmon.rido.presentation.editor.ParentTaskChoice
import io.github.peanutpersimmon.rido.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rido.ui.theme.AppTheme

fun selectableChoices(all: List<EditorChoice>, selectedIds: Set<Long>): List<EditorChoice> =
    all.filter { !it.isArchived || it.id in selectedIds }

@Composable
fun TagPicker(choices: List<EditorChoice>, selectedIds: Set<Long>, onSelected: (Set<Long>) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val live = io.github.peanutpersimmon.rido.ui.feature.settings.LocalMetadataState.current
    val model = io.github.peanutpersimmon.rido.ui.feature.settings.LocalMetadataViewModel.current
    val tags = if (model != null) live.tags else choices.map {
        io.github.peanutpersimmon.rido.domain.model.Tag(it.id, it.name, it.isArchived, 0, 0, 0, it.parentId)
    }
    val selected = tags.filter { it.id in selectedIds }
    if (selected.isEmpty()) EditorTool(Icons.AutoMirrored.Outlined.Label, "标签", onClick = { open = true })
    else selected.forEach { tag ->
        EditorSelectionChip(
            icon = Icons.AutoMirrored.Outlined.Label,
            label = "标签",
            text = tag.name,
            onClick = { open = true },
        )
    }
    if (open) io.github.peanutpersimmon.rido.ui.feature.tags.TagSelectionSheet(
        tags, selectedIds, onSelected, { open = false })
}

@Composable
fun ParentTaskPicker(choices: List<ParentTaskChoice>, selectedId: Long?, onSelected: (Long?) -> Unit, label: String = "关联任务") {
    var open by remember { mutableStateOf(false) }
    EditorTool(if (label == "父任务") Icons.Outlined.SubdirectoryArrowRight else Icons.Outlined.Link, label, choices.firstOrNull { it.id == selectedId }?.title, { open = true })
    if (open) AppBottomSheet(label, { open = false },
        bottomVisual = io.github.peanutpersimmon.rido.ui.components.SheetBottomVisual.BodyTextRow,
        headerAction = { EditorIconAction(Icons.Outlined.Close, "关闭$label", { open = false }, compactSheetAction = true) }) {
        androidx.compose.foundation.layout.Column {
            ChoiceRow("未选择", selectedId == null, sheetStyle = true) { onSelected(null); open = false }
            choices.forEach { choice ->
                ChoiceRow(choice.title, choice.id == selectedId, sheetStyle = true) { onSelected(choice.id); open = false }
            }
        }
    }
}

@Composable
fun ChoiceRow(
    label: String,
    selected: Boolean,
    sheetStyle: Boolean = false,
    showSelectionIndicator: Boolean = sheetStyle,
    onClick: () -> Unit,
) {
    val d = AppTheme.dimens
    Box(
        modifier = Modifier.fillMaxWidth()
            .then(if (sheetStyle) Modifier.heightIn(min = d.sheetRowVisualHeight - d.spaceXs) else Modifier.height(38.dp))
            .then(if (sheetStyle) Modifier else Modifier.clip(AppTheme.shapes.small)
                .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
                    else MaterialTheme.colorScheme.surface))
            .clickable(onClick = onClick)
            .semantics { this.selected = selected },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (sheetStyle) d.sheetRowVisualHeight - d.spaceXs else 38.dp)
                .padding(horizontal = d.spaceS,
                    vertical = if (sheetStyle) d.spaceS - d.spaceXs / 2 else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                Modifier.weight(1f),
                style = if (sheetStyle) AppTheme.type.body else AppTheme.type.fieldValue,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (sheetStyle) Int.MAX_VALUE else 1,
                overflow = if (sheetStyle) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
            if (showSelectionIndicator) Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Outlined.Check, contentDescription = null,
                    Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
