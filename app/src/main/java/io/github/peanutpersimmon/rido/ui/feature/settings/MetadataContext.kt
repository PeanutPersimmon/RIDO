package io.github.peanutpersimmon.rido.ui.feature.settings

import androidx.compose.runtime.staticCompositionLocalOf
import io.github.peanutpersimmon.rido.presentation.settings.MetadataViewModel
import io.github.peanutpersimmon.rido.presentation.settings.MetadataUiState

val LocalMetadataViewModel = staticCompositionLocalOf<MetadataViewModel?> { null }
val LocalMetadataState = staticCompositionLocalOf { MetadataUiState() }
