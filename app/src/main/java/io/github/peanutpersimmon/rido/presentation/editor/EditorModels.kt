package io.github.peanutpersimmon.rido.presentation.editor

import io.github.peanutpersimmon.rido.domain.model.Category
import io.github.peanutpersimmon.rido.domain.model.DomainError
import io.github.peanutpersimmon.rido.domain.model.Tag
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class EditorMode { CREATE, EDIT }

data class EditorChoice(
    val id: Long,
    val name: String,
    val isArchived: Boolean = false,
    val parentId: Long? = null,
)

data class ParentTaskChoice(val id: Long, val title: String, val status: TaskStatus = TaskStatus.IN_PROGRESS)

data class EditorOptionsUiModel(
    val categories: List<EditorChoice> = emptyList(),
    val tags: List<EditorChoice> = emptyList(),
    val parentTasks: List<ParentTaskChoice> = emptyList(),
)

fun editorOptions(
    categories: List<Category>,
    tags: List<Tag>,
    tasks: List<Task>,
    excludedTaskId: Long? = null,
) = EditorOptionsUiModel(
    categories = categories.map { EditorChoice(it.id, it.name, it.isArchived) },
    tags = tags.map { EditorChoice(it.id, it.name, it.isArchived, it.parentId) },
    parentTasks = tasks.filter { it.id != excludedTaskId }.map { ParentTaskChoice(it.id, it.title, it.status) },
)

fun DomainError.userMessage(): String = when (this) {
    DomainError.EmptyTitle -> "标题不能为空"
    DomainError.InvalidTime -> "时间设置无效"
    DomainError.SleepIntervalTooLong -> "这条睡眠记录已超过 24 小时，请确认实际起床时间"
    DomainError.SessionOverlap -> "这段时间与已有记录重叠"
    DomainError.NoActiveSession -> "当前没有正在进行的记录"
    DomainError.ParentCycle -> "父任务关系不能形成循环"
    DomainError.InvalidKeywordRule -> "关键词规则无效"
    DomainError.CommandAlreadyProcessed -> "该操作已经处理"
    is DomainError.NotFound -> "记录不存在或已被删除"
    is DomainError.DataIntegrity -> reason
}

fun localDateTimeMillis(date: LocalDate, time: LocalTime, zoneId: ZoneId): Long =
    date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()

fun Long.toLocalDate(zoneId: ZoneId): LocalDate = Instant.ofEpochMilli(this).atZone(zoneId).toLocalDate()
fun Long.toLocalTime(zoneId: ZoneId): LocalTime = Instant.ofEpochMilli(this).atZone(zoneId).toLocalTime().withSecond(0).withNano(0)
