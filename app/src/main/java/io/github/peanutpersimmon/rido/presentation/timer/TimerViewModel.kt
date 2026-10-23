package io.github.peanutpersimmon.rido.presentation.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rido.domain.model.DomainResult
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.presentation.editor.userMessage
import io.github.peanutpersimmon.rido.domain.usecase.GetClassificationLabelsUseCase
import io.github.peanutpersimmon.rido.domain.usecase.ObserveActiveSessionUseCase
import io.github.peanutpersimmon.rido.domain.usecase.StopActiveSessionUseCase
import io.github.peanutpersimmon.rido.presentation.time.CurrentTimeProvider
import io.github.peanutpersimmon.rido.presentation.time.SystemCurrentTimeProvider
import io.github.peanutpersimmon.rido.presentation.time.alignedSecondTicks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class TimerViewModel(
    observeActiveSession: ObserveActiveSessionUseCase,
    private val stopActiveSession: StopActiveSessionUseCase,
    private val getLabels: GetClassificationLabelsUseCase,
    private val timeProvider: CurrentTimeProvider = SystemCurrentTimeProvider,
    private val stopAndComplete: io.github.peanutpersimmon.rido.domain.usecase.StopSessionAndCompleteTaskUseCase? = null,
) : ViewModel() {
    private val stopping = MutableStateFlow(false)
    private val stopError = MutableStateFlow<String?>(null)
    private val activeDetails = observeActiveSession().onEach { tagged ->
        if (tagged == null) stopping.value = false
    }.mapLatest { tagged ->
        val category = tagged?.session?.categoryId?.let { getLabels().categoriesById[it] }
        tagged to category
    }

    val uiState = combine(
        activeDetails,
        alignedSecondTicks(timeProvider),
        stopping,
        stopError,
    ) { (tagged, category), nowMillis, isStopping, errorMessage ->
        if (tagged == null) {
            TimerUiState.Inactive
        } else {
            TimerUiState.Active(
                sessionId = tagged.session.id, hasLinkedTask = tagged.session.taskId != null,
                kind = tagged.session.kind,
                startAtMillis = tagged.session.startAtMillis,
                title = tagged.session.title,
                categoryLabel = category?.name,
                categoryColorKey = category?.colorKey,
                elapsed = elapsedFrom(tagged.session.startAtMillis, nowMillis),
                isStopping = isStopping,
                errorMessage = errorMessage,
            )
        }
    }.catch {
        emit(TimerUiState.Inactive)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TimerUiState.Loading,
    )

    fun stop() = finish(false)
    fun stopAndCompleteTask() = finish(true)

    private fun finish(complete: Boolean) {
        if (stopping.value) return
        val active = uiState.value as? TimerUiState.Active ?: return
        if (active.kind != SessionKind.ACTIVITY) {
            stopError.value = "睡眠记录请使用睡眠编辑或起床操作"
            return
        }
        if (complete && (!active.hasLinkedTask || stopAndComplete == null)) return
        stopping.value = true
        viewModelScope.launch {
            stopping.value = true
            stopError.value = null
            when (val result = if (complete) requireNotNull(stopAndComplete)(timeProvider.nowMillis()) else stopActiveSession(timeProvider.nowMillis())) {
                is DomainResult.Failure -> {
                    stopping.value = false
                    stopError.value = result.error.userMessage()
                }
                is DomainResult.Success -> Unit
            }
        }
    }

    fun clearError() {
        stopError.update { null }
    }

    companion object {
        fun factory(
            observeActiveSession: ObserveActiveSessionUseCase,
            stopActiveSession: StopActiveSessionUseCase,
            getLabels: GetClassificationLabelsUseCase,
            stopAndComplete: io.github.peanutpersimmon.rido.domain.usecase.StopSessionAndCompleteTaskUseCase? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = TimerViewModel(
                observeActiveSession,
                stopActiveSession,
                getLabels, stopAndComplete = stopAndComplete,
            ) as T
        }
    }
}
