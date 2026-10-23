package io.github.peanutpersimmon.rido.domain.model

data class Task(
    val id: Long = 0,
    val title: String,
    val note: String? = null,
    val location: String? = null,
    val status: TaskStatus = TaskStatus.TODO,
    val parentTaskId: Long? = null,
    val plannedDateEpochDay: Long? = null,
    val plannedStartMinuteOfDay: Int? = null,
    val plannedReminderOffsetMinutes: Int? = null,
    val dueDateEpochDay: Long? = null,
    val dueMinuteOfDay: Int? = null,
    val dueReminderOffsetMinutes: Int? = null,
    val estimatedDurationMinutes: Int? = null,
    val categoryId: Long? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val completedAtMillis: Long? = null,
    val cancelledAtMillis: Long? = null,
    val completionSource: TaskCompletionSource? = null,
)

data class Event(
    val id: Long = 0,
    val title: String,
    val note: String? = null,
    val location: String? = null,
    val dateEpochDay: Long,
    val startMinuteOfDay: Int? = null,
    val startReminderOffsetMinutes: Int? = null,
    val endDateEpochDay: Long? = null,
    val endMinuteOfDay: Int? = null,
    val categoryId: Long? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class Session(
    val id: Long = 0,
    val title: String,
    val note: String? = null,
    val location: String? = null,
    val kind: SessionKind = SessionKind.ACTIVITY,
    val temporalType: SessionTemporalType = SessionTemporalType.INTERVAL,
    val startAtMillis: Long,
    val endAtMillis: Long? = null,
    val taskId: Long? = null,
    val categoryId: Long? = null,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val sourceEventId: Long? = null,
)

data class Category(
    val id: Long = 0,
    val name: String,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val colorKey: String = CategoryColorKey.NEUTRAL.storageKey,
)

data class Tag(
    val id: Long = 0,
    val name: String,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val parentId: Long? = null,
)

data class KeywordRule(
    val id: Long = 0,
    val keyword: String,
    val categoryId: Long? = null,
    val tagIds: Set<Long> = emptySet(),
    val isEnabled: Boolean = true,
    val priority: Int = 0,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

data class ProcessedCommand(
    val commandId: String,
    val processedAtMillis: Long,
)
