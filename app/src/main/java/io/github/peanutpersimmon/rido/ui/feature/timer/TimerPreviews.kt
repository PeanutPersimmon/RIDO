package io.github.peanutpersimmon.rido.ui.feature.timer

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import io.github.peanutpersimmon.rido.presentation.timer.TimerUiState
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.presentation.timer.elapsedFrom
import io.github.peanutpersimmon.rido.ui.theme.RidoTheme

@Preview
@Composable
private fun TimerPreview() {
    RidoTheme {
        TimerScreenContent(
            uiState = TimerUiState.Active(
                sessionId = 1,
                kind = SessionKind.ACTIVITY,
                startAtMillis = 0,
                title = "做 PPT",
                categoryLabel = "工作",
                elapsed = elapsedFrom(0, 42 * 60_000L + 17_000L),
                isStopping = false,
                errorMessage = null,
            ),
            onBack = {},
            onStop = {},
        )
    }
}
