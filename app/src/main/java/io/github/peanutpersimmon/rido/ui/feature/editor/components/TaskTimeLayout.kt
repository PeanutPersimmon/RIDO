package io.github.peanutpersimmon.rido.ui.feature.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.peanutpersimmon.rido.ui.theme.AppTheme

/** Three compact icon-first entries; the planned interval is edited within one entry. */
@Composable
fun TaskTimeLayout(
    planned: @Composable (Modifier) -> Unit,
    deadline: @Composable (Modifier) -> Unit,
    estimate: @Composable (Modifier) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppTheme.dimens.editorTimeRowGap),
    ) {
        planned(Modifier.fillMaxWidth())
        deadline(Modifier.fillMaxWidth())
        estimate(Modifier.fillMaxWidth())
    }
}
