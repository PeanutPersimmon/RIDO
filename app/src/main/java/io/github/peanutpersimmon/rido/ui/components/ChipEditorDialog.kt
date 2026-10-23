package io.github.peanutpersimmon.rido.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import io.github.peanutpersimmon.rido.ui.theme.AppTheme

/**
 * Domain-neutral editor for collecting a batch of string chips before persistence.
 * Persistence errors are supplied by [errorMessage]; this dialog never dismisses itself.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipEditorDialog(
    title: String,
    placeholder: String,
    existingValues: List<String>,
    normalize: (String) -> String,
    validateDraft: (draft: String, pending: List<String>, existing: List<String>) -> String?,
    onSubmit: (List<String>) -> Unit,
    itemLabel: (String) -> String,
    emptyMessage: String,
    duplicateMessage: String,
    errorMessage: String? = null,
    onDismissRequest: () -> Unit,
) {
    var input by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var pending by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current

    fun addPending() {
        val normalized = normalize(input.text)
        localError = when {
            normalized.isBlank() -> emptyMessage
            pending.any { normalize(it) == normalized } -> duplicateMessage
            existingValues.any { normalize(it) == normalized } -> duplicateMessage
            else -> validateDraft(normalized, pending, existingValues)
        }
        if (localError == null) {
            pending = pending + normalized
            input = TextFieldValue()
        }
    }

    AppDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        actions = {
            ChipDialogAction(
                text = "完成",
                enabled = pending.isNotEmpty(),
                onClick = {
                    keyboard?.hide()
                    onSubmit(pending.toList())
                },
            )
        },
    ) {
        CompactChipInput(
            value = input,
            onValueChange = {
                input = it
                localError = null
            },
            placeholder = placeholder,
            submitEnabled = input.text.isNotBlank(),
            onSubmit = ::addPending,
        )
        (localError ?: errorMessage)?.let { message ->
            Text(
                text = message,
                style = AppTheme.type.caption,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Text(
            text = "已添加",
            style = AppTheme.type.caption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (pending.isEmpty()) {
            Text(
                text = "还没有待添加项",
                style = AppTheme.type.fieldValue,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS),
                verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS),
            ) {
                pending.forEach { value ->
                    PendingChip(
                        label = itemLabel(value),
                        onRemove = { pending = pending - value },
                    )
                }
            }
        }
    }
}

/** Shared visual and validation skeleton for editing one chip value. */
@Composable
fun ChipValueEditDialog(
    title: String,
    initialValue: String,
    placeholder: String,
    existingValues: List<String>,
    normalize: (String) -> String,
    validateDraft: (String) -> String?,
    onSubmit: (String) -> Unit,
    emptyMessage: String,
    duplicateMessage: String,
    errorMessage: String? = null,
    onDismissRequest: () -> Unit,
) {
    var value by rememberSaveable(initialValue) { mutableStateOf(initialValue) }
    var localError by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        val normalized = normalize(value)
        localError = when {
            normalized.isBlank() -> emptyMessage
            existingValues.any { normalize(it) == normalized } -> duplicateMessage
            else -> validateDraft(normalized)
        }
        if (localError == null) onSubmit(normalized)
    }

    AppDialog(
        title = title,
        onDismissRequest = onDismissRequest,
        actions = {
            ChipDialogAction("取消", enabled = true, onClick = onDismissRequest)
            ChipDialogAction("保存", enabled = value.isNotBlank(), onClick = ::submit)
        },
    ) {
        AppTextField(
            value = value,
            onValueChange = {
                value = it
                localError = null
            },
            label = placeholder,
            singleLine = true,
            stacked = true,
            showLabel = false,
        )
        (localError ?: errorMessage)?.let { message ->
            Text(
                text = message,
                style = AppTheme.type.caption,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun CompactChipInput(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    submitEnabled: Boolean,
    onSubmit: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val d = AppTheme.dimens
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(d.tagRowHeight),
            shape = AppTheme.shapes.small,
            color = colors.surfaceContainerLow,
            border = BorderStroke(d.border, colors.outlineVariant),
            shadowElevation = d.spaceMicro,
            tonalElevation = d.spaceMicro,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .wrapContentHeight()
                    .focusRequester(focusRequester)
                    .padding(horizontal = d.spaceM)
                    .testTag("chip_editor_input"),
                singleLine = true,
                textStyle = AppTheme.type.editorInput.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.onSurface),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (submitEnabled) onSubmit() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) {
                            Text(
                                text = placeholder,
                                style = AppTheme.type.editorInput,
                                color = colors.onSurfaceVariant,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
        Spacer(Modifier.width(d.spaceS))
        Box(
            modifier = Modifier
                .size(d.minimumTouchTarget)
                .clickable(enabled = submitEnabled, onClick = onSubmit),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.size(d.tagRowHeight),
                shape = AppTheme.shapes.small,
                color = if (submitEnabled) colors.onSurface else colors.surfaceContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = "确认输入",
                        modifier = Modifier.size(d.reorderHandleIconSize),
                        tint = if (submitEnabled) colors.surface else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingChip(label: String, onRemove: () -> Unit) {
    val d = AppTheme.dimens
    Surface(
        shape = AppTheme.shapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(d.border, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(start = d.spaceM),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = AppTheme.type.editorDescription)
            Box(
                modifier = Modifier
                    .size(d.minimumTouchTarget)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "移除 $label",
                    modifier = Modifier.size(d.reorderHandleIconSize),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ChipDialogAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        modifier = Modifier
            .heightIn(min = AppTheme.dimens.minimumTouchTarget)
            .clickable(enabled = enabled, onClick = onClick)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = AppTheme.dimens.spaceS),
        style = AppTheme.type.fieldLabel,
        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
