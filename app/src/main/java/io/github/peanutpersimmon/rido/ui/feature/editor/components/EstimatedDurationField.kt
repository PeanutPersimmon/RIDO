package io.github.peanutpersimmon.rido.ui.feature.editor.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import io.github.peanutpersimmon.rido.ui.components.AppBottomSheet
import io.github.peanutpersimmon.rido.ui.components.picker.WheelPicker
import io.github.peanutpersimmon.rido.ui.theme.AppTheme

private val durationPresets = listOf(15, 30, 45, 60, 90, 120, 180)
internal fun durationLabel(total: Int): String = when {
    total < 60 -> "${total}m"
    total % 60 == 0 -> "${total / 60}h"
    else -> "${total / 60}h${total % 60}m"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EstimatedDurationField(
    hours: String,
    minutes: String,
    modifier: Modifier = Modifier,
    onChange: (String, String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val total = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
    PickerField(
        "预计",
        EditorTimeRole.ESTIMATE,
        if (total > 0) durationLabel(total) else "未填写",
        { open = true },
        modifier,
    )
    if (open) {
        var custom by remember { mutableStateOf(false) }
        var draftHours by remember { mutableIntStateOf(total / 60) }
        var draftMinutes by remember { mutableIntStateOf(total % 60) }
        // Keep legacy non-step values intact; never silently round a stored estimate.
        val hourValues = remember { (0..maxOf(99, total / 60)).toList() }
        val minuteValues = remember { ((0..55 step 5).toList() + total % 60).distinct().sorted() }
        fun commit(value: Int) {
            onChange((value / 60).takeIf { it > 0 }?.toString().orEmpty(),
                (value % 60).takeIf { it > 0 }?.toString().orEmpty())
            open = false
        }
        AppBottomSheet("预计用时", { open = false },
            bottomVisual = io.github.peanutpersimmon.rido.ui.components.SheetBottomVisual.Special,
            headerActions = {
            if (custom) EditorIconAction(Icons.Outlined.Check, "确认预计用时",
                { commit(draftHours * 60 + draftMinutes) }, compactSheetAction = true)
            EditorIconAction(Icons.Outlined.Close, "关闭预计用时", { open = false }, compactSheetAction = true)
        }) {
            Column(verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceXs)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.dimens.spaceS)) {
                    durationPresets.forEach { value ->
                        EditorTextAction(durationLabel(value), { commit(value) })
                    }
                    EditorTextAction("自定义", { custom = true })
                }
                if (custom) Row(Modifier.fillMaxWidth()) {
                    WheelPicker(hourValues, draftHours, { draftHours = it }, { "${it}小时" }, Modifier.weight(1f))
                    WheelPicker(minuteValues, draftMinutes, { draftMinutes = it }, { "${it}分钟" }, Modifier.weight(1f))
                }
                if (total > 0) EditorTextAction("清除预计", { commit(0) })
            }
        }
    }
}
