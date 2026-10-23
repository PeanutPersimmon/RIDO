package io.github.peanutpersimmon.rido.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.peanutpersimmon.rido.data.local.room.entity.EventEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.EventTagCrossRef
import io.github.peanutpersimmon.rido.data.local.room.relation.EventWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("""
        SELECT name FROM (
            SELECT TRIM(location) AS name, updatedAtMillis AS usedAt FROM events
            WHERE location IS NOT NULL AND TRIM(location) != ''
            UNION ALL
            SELECT TRIM(location) AS name, updatedAtMillis AS usedAt FROM sessions
            WHERE location IS NOT NULL AND TRIM(location) != ''
            UNION ALL
            SELECT TRIM(location) AS name, updatedAtMillis AS usedAt FROM tasks
            WHERE location IS NOT NULL AND TRIM(location) != ''
        ) GROUP BY name ORDER BY MAX(usedAt) DESC, name ASC LIMIT 5
    """)
    suspend fun recentLocations(): List<String>

    @Transaction
    @Query("SELECT * FROM events ORDER BY dateEpochDay, startMinuteOfDay, id")
    fun observeAllWithTags(): Flow<List<EventWithTags>>

    @Query("""SELECT * FROM events WHERE dateEpochDay <= :lastEpochDay
        AND COALESCE(endDateEpochDay, dateEpochDay) >= :firstEpochDay
        ORDER BY dateEpochDay, startMinuteOfDay, id""")
    suspend fun getScheduledInRange(firstEpochDay: Long, lastEpochDay: Long): List<EventEntity>

    @Insert
    suspend fun insert(event: EventEntity): Long

    @Update
    suspend fun update(event: EventEntity)

    @Delete
    suspend fun delete(event: EventEntity)

    @Query("DELETE FROM events WHERE id = :eventId")
    suspend fun deleteById(eventId: Long): Int

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getById(id: Long): EventEntity?

    @Query("SELECT * FROM events ORDER BY id")
    suspend fun getAll(): List<EventEntity>

    @Query("SELECT * FROM events WHERE id IN (:ids) ORDER BY id")
    suspend fun getByIds(ids: Set<Long>): List<EventEntity>

    @Transaction
    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun getWithTags(id: Long): EventWithTags?

    @Transaction
    @Query(
        """
        SELECT * FROM events
        WHERE (dateEpochDay <= :lastEpochDay AND COALESCE(endDateEpochDay, dateEpochDay) >= :firstEpochDay)
        ORDER BY dateEpochDay, startMinuteOfDay, id
        """,
    )
    suspend fun getProjectionCandidatesWithTags(
        firstEpochDay: Long,
        lastEpochDay: Long,
    ): List<EventWithTags>

    @Insert
    suspend fun insertTagRefs(refs: List<EventTagCrossRef>)

    @Query("DELETE FROM event_tag_cross_refs WHERE eventId = :eventId")
    suspend fun deleteTagRefs(eventId: Long)

    @Transaction
    suspend fun replaceTags(eventId: Long, tagIds: Set<Long>) {
        deleteTagRefs(eventId)
        if (tagIds.isNotEmpty()) {
            insertTagRefs(tagIds.map { tagId -> EventTagCrossRef(eventId, tagId) })
        }
    }
}

