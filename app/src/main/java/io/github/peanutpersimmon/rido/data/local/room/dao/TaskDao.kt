package io.github.peanutpersimmon.rido.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.peanutpersimmon.rido.data.local.room.entity.TaskEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.TaskTagCrossRef
import io.github.peanutpersimmon.rido.data.local.room.relation.TaskParentLink
import io.github.peanutpersimmon.rido.data.local.room.relation.TaskWithTags
import io.github.peanutpersimmon.rido.domain.model.TaskCompletionSource
import io.github.peanutpersimmon.rido.domain.model.TaskStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Transaction
    @Query("SELECT * FROM tasks ORDER BY createdAtMillis, id")
    fun observeAllWithTags(): Flow<List<TaskWithTags>>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteById(taskId: Long): Int

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks ORDER BY createdAtMillis, id")
    suspend fun getAll(): List<TaskEntity>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getWithTags(id: Long): TaskWithTags?

    @Query("SELECT * FROM tasks WHERE parentTaskId = :parentId ORDER BY createdAtMillis, id")
    suspend fun getChildren(parentId: Long): List<TaskEntity>

    @Query("SELECT COUNT(*) FROM tasks WHERE parentTaskId = :parentId AND status != 'DONE'")
    suspend fun countUnfinishedChildren(parentId: Long): Int

    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY createdAtMillis DESC, id DESC")
    suspend fun getByStatus(status: TaskStatus): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE plannedDateEpochDay = :epochDay ORDER BY plannedStartMinuteOfDay, id")
    suspend fun getPlannedOnDate(epochDay: Long): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE dueDateEpochDay = :epochDay ORDER BY dueMinuteOfDay, id")
    suspend fun getDueOnDate(epochDay: Long): List<TaskEntity>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE ((plannedDateEpochDay <= :lastEpochDay OR (plannedDateEpochDay IS NULL AND dueDateEpochDay IS NOT NULL))
               AND createdAtMillis < :rangeEndExclusive
               AND (completedAtMillis IS NULL OR completedAtMillis >= :rangeStartInclusive))
           OR (plannedDateEpochDay BETWEEN :firstEpochDay AND :lastEpochDay)
           OR (completedAtMillis IS NOT NULL
               AND completedAtMillis >= :rangeStartInclusive
               AND completedAtMillis < :rangeEndExclusive)
        ORDER BY COALESCE(plannedDateEpochDay, 9223372036854775807),
                 plannedStartMinuteOfDay,
                 completedAtMillis,
                 id
        """,
    )
    suspend fun getProjectionCandidatesWithTags(
        firstEpochDay: Long,
        lastEpochDay: Long,
        rangeStartInclusive: Long,
        rangeEndExclusive: Long,
    ): List<TaskWithTags>

    @Query("SELECT id, parentTaskId FROM tasks")
    suspend fun getParentLinks(): List<TaskParentLink>

    @Query(
        """
        UPDATE tasks
        SET status = :status,
            completedAtMillis = :completedAtMillis,
            cancelledAtMillis = :cancelledAtMillis,
            completionSource = :completionSource,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :taskId
        """,
    )
    suspend fun updateState(
        taskId: Long,
        status: TaskStatus,
        completedAtMillis: Long?,
        cancelledAtMillis: Long?,
        completionSource: TaskCompletionSource?,
        updatedAtMillis: Long,
    )

    @Insert
    suspend fun insertTagRefs(refs: List<TaskTagCrossRef>)

    @Query("DELETE FROM task_tag_cross_refs WHERE taskId = :taskId")
    suspend fun deleteTagRefs(taskId: Long)

    @Transaction
    suspend fun replaceTags(taskId: Long, tagIds: Set<Long>) {
        deleteTagRefs(taskId)
        if (tagIds.isNotEmpty()) {
            insertTagRefs(tagIds.map { tagId -> TaskTagCrossRef(taskId, tagId) })
        }
    }
}
