package io.github.peanutpersimmon.rido.presentation.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import io.github.peanutpersimmon.rido.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rido.domain.usecase.*
import io.github.peanutpersimmon.rido.presentation.editor.event.EventEditorViewModel
import io.github.peanutpersimmon.rido.presentation.editor.session.SessionEditorViewModel
import io.github.peanutpersimmon.rido.presentation.editor.task.TaskEditorViewModel

class EditorViewModelFactory(private val repository: RidoCoreRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        TaskEditorViewModel::class.java -> TaskEditorViewModel(
            GetTaskForEditorUseCase(repository), GetEditorOptionsUseCase(repository), SaveTaskUseCase(repository),
            DeleteTaskUseCase(repository), CompleteTaskUseCase(repository), ReopenTaskUseCase(repository),
            initialClassification = InitialClassificationUseCase(repository),
        )
        EventEditorViewModel::class.java -> EventEditorViewModel(
            GetEventForEditorUseCase(repository), GetEditorOptionsUseCase(repository), SaveEventUseCase(repository),
            DeleteEventUseCase(repository), RestoreEventUseCase(repository),
            initialClassification = InitialClassificationUseCase(repository),
        )
        SessionEditorViewModel::class.java -> SessionEditorViewModel(
            GetSessionForEditorUseCase(repository), GetEditorOptionsUseCase(repository), CreatePastSessionUseCase(repository),
            StartSessionUseCase(repository), SaveSessionEditUseCase(repository), DeleteSessionWithUndoUseCase(repository),
            RestoreSessionUseCase(repository),
            getEvent = GetEventForEditorUseCase(repository), observeChanges = ObserveTimelineDataChangesUseCase(repository),
            createPastAndComplete = CreatePastSessionAndCompleteTaskUseCase(repository),
            getPreviousActualEnd = GetPreviousActualEndUseCase(repository),
            initialClassification = InitialClassificationUseCase(repository),
        )
        else -> error("Unsupported editor ViewModel: ${modelClass.name}")
    } as T
}
