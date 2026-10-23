package io.github.peanutpersimmon.rido.ui.feature.timer

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import io.github.peanutpersimmon.rido.R
import io.github.peanutpersimmon.rido.ui.components.AppClickableSurface
import io.github.peanutpersimmon.rido.ui.theme.AppTheme

@Composable
fun TimerControls(
    isStopping: Boolean,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    onStopAndComplete: (() -> Unit)? = null,
    containerColor: Color = AppTheme.colors.timerPanel,
    contentColor: Color = AppTheme.colors.timerPrimaryText,
) {
    androidx.compose.foundation.layout.Column {
        AppClickableSurface(
            onClick = onStop,
            enabled = !isStopping,
            modifier = modifier.fillMaxWidth(),
            shape = AppTheme.shapes.medium,
            containerColor = containerColor,
        ) {
            Text(
                text = if (isStopping) stringResource(R.string.timer_stopping) else "结束计时",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppTheme.dimens.spaceM),
                color = contentColor,
                style = AppTheme.type.itemTitle,
                textAlign = TextAlign.Center,
            )
        }
        onStopAndComplete?.let { finish ->
            AppClickableSurface(onClick = finish, enabled = !isStopping,
                modifier = Modifier.fillMaxWidth(), containerColor = containerColor) {
                Text("结束计时并完成任务", Modifier.fillMaxWidth().padding(AppTheme.dimens.spaceM),
                    color = contentColor, style = AppTheme.type.secondary, textAlign = TextAlign.Center)
            }
        }
    }
}
