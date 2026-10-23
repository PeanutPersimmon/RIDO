package io.github.peanutpersimmon.rido.ui.feature.editor.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.peanutpersimmon.rido.presentation.editor.EditorChoice
import io.github.peanutpersimmon.rido.ui.theme.RidoTheme

private val previewChoices = listOf(EditorChoice(1, "工作"), EditorChoice(2, "科研", true))

@Preview(showBackground = true)
@Composable
private fun CategoryPickerPreview() = RidoTheme {
    CategoryPicker(previewChoices, 2) {}
}

@Preview(showBackground = true)
@Composable
private fun TagPickerPreview() = RidoTheme {
    Column { TagPicker(previewChoices, setOf(1, 2)) {} }
}
