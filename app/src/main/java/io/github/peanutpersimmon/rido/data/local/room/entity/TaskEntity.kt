package io.github.peanutpersimmon.rido.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.peanutpersimmon.rido.domain.model.TaskCompletionSource
import io.github.peanutpersimmon.rido.domain.model.TaskStatus

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentTaskId"],
            onDelete = ForeignKey.SET_NULL,
            deferred = true,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("status"),
        Index("parentTaskId"),
        Index("plannedDateEpochDay"),
        Index("dueDateEpochDay"),
        Index("categoryId"),
        Index("completedAtMillis"),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String? = null,
    val location: String? = null,
    val status: TaskStatus,
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

