package io.github.peanutpersimmon.rido.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.peanutpersimmon.rido.R
import io.github.peanutpersimmon.rido.ui.components.EmptyState

/** Intentionally non-functional: confirms only the root layout and navigation skeleton. */
@Composable
fun PlaceholderScreen(body: String, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.skeleton_ready),
        body = body,
        modifier = modifier,
    )
}

