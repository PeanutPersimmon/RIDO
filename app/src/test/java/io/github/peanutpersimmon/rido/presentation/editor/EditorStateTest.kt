package io.github.peanutpersimmon.rido.presentation.editor

import io.github.peanutpersimmon.rido.domain.model.Category
import io.github.peanutpersimmon.rido.domain.model.Tag
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.presentation.editor.event.EventEditorUiState
import io.github.peanutpersimmon.rido.presentation.editor.session.SessionEditorUiState
import io.github.peanutpersimmon.rido.presentation.editor.task.TaskEditorUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorStateTest {
    @Test fun taskCreateStartsWithNullableCategoryAndEmptyTags() {
        val state = TaskEditorUiState()
        assertNull(state.categoryId)
        assertTrue(state.tagIds.isEmpty())
    }

    @Test fun blankTaskTitleCannotSave() = assertFalse(TaskEditorUiState(title = " ").canSave)
    @Test fun titleMakesTaskBasicallySavable() = assertTrue(TaskEditorUiState(title = "任务").canSave)
    @Test fun eventDateOnlyCanSave() = assertTrue(EventEditorUiState(title = "会议").canSave)
    @Test fun sessionCreateWithOnlyOccurrenceTimeCanSavePoint() {
        val state = SessionEditorUiState(title = "记录")
        assertTrue(state.canSavePast)
        assertEquals(SessionTemporalType.POINT, state.saveTemporalType)
    }
    @Test fun sessionCreateWithTitleMayStartNow() = assertTrue(SessionEditorUiState(title = "记录").canStartNow)

    @Test fun editorOptionsExcludeCurrentTaskButKeepArchiveMetadata() {
        val result = editorOptions(
            listOf(Category(3, "旧分类", true, createdAtMillis = 0, updatedAtMillis = 0)),
            listOf(Tag(4, "旧标签", true, createdAtMillis = 0, updatedAtMillis = 0)),
            listOf(Task(5, "自己", createdAtMillis = 0, updatedAtMillis = 0), Task(6, "父项", createdAtMillis = 0, updatedAtMillis = 0)),
            5,
        )
        assertTrue(result.categories.single().isArchived)
        assertTrue(result.tags.single().isArchived)
        assertEquals(listOf(6L), result.parentTasks.map { it.id })
    }
}
