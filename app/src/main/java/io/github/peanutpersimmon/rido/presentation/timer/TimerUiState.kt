package io.github.peanutpersimmon.rido.presentation.timer

import io.github.peanutpersimmon.rido.domain.model.SessionKind

sealed interface TimerUiState {
    data object Loading : TimerUiState
    data object Inactive : TimerUiState

    data class Active(
        val sessionId: Long,
        val kind: SessionKind,
        val startAtMillis: Long,
        val title: String,
        val categoryLabel: String?,
        val categoryColorKey: String? = null,
        val elapsed: TimerElapsedUiModel,
        val isStopping: Boolean,
        val errorMessage: String?,
        val hasLinkedTask: Boolean = false,
    ) : TimerUiState
}

