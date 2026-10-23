package io.github.peanutpersimmon.rido.ui.feature.editor.task

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import io.github.peanutpersimmon.rido.ui.feature.editor.components.*
import io.github.peanutpersimmon.rido.ui.theme.RidoTheme

@Preview(showBackground = true, widthDp = 320, heightDp = 640)
@Composable
private fun TaskEditorPreview() = RidoTheme {
    EditorLayout("新建任务", true, remember { SnackbarHostState() }, {}, {}) {
        TitleField("完成组会 PPT", {})
        NoteField("整理图表并检查结论", {})
        EditorTimeModule {
            DateTimeField("开始", EditorTimeRole.START, null, null, {})
            DateTimeField("截止", EditorTimeRole.END, null, null, {})
            EstimatedDurationField("", "") { _, _ -> }
        }
        EditorToolGroup {
            CategoryPicker(emptyList(), null) {}
            TagPicker(emptyList(), emptySet()) {}
        }
    }
}
