package io.github.peanutpersimmon.rido.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import io.github.peanutpersimmon.rido.data.local.room.entity.SessionEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.SessionTagCrossRef
import io.github.peanutpersimmon.rido.data.local.room.relation.SessionWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Transaction
    @Query("SELECT * FROM sessions ORDER BY startAtMillis, id")
    fun observeAllWithTags(): Flow<List<SessionWithTags>>

    @Transaction
    @Query("SELECT * FROM sessions WHERE temporalType = 'INTERVAL' AND endAtMillis IS NULL ORDER BY startAtMillis DESC LIMIT 1")
    fun observeActiveWithTags(): Flow<SessionWithTags?>

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Delete
    suspend fun delete(session: SessionEntity)

    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getById(id: Long): SessionEntity?

    @Transaction
    @Query("SELECT * FROM sessions WHERE id = :id")
    suspend fun getWithTags(id: Long): SessionWithTags?

    @Query("SELECT * FROM sessions WHERE temporalType = 'INTERVAL' AND endAtMillis IS NULL ORDER BY startAtMillis DESC LIMIT 1")
    suspend fun getActiveSession(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE temporalType = 'INTERVAL' AND endAtMillis IS NULL ORDER BY startAtMillis, id")
    suspend fun getActiveSessions(): List<SessionEntity>

    @Query("SELECT COUNT(*) FROM sessions WHERE temporalType = 'INTERVAL' AND endAtMillis IS NULL")
    suspend fun countActiveSessions(): Int

    @Query(
        """
        SELECT * FROM sessions
        WHERE (temporalType = 'POINT' AND startAtMillis >= :rangeStartInclusive AND startAtMillis < :rangeEndExclusive)
           OR (temporalType = 'INTERVAL' AND startAtMillis < :rangeEndExclusive
               AND COALESCE(endAtMillis, 9223372036854775807) > :rangeStartInclusive)
        ORDER BY startAtMillis, id
        """,
    )
    suspend fun getIntersecting(
        rangeStartInclusive: Long,
        rangeEndExclusive: Long,
    ): List<SessionEntity>

    @Transaction
    @Query(
        """
        SELECT * FROM sessions
        WHERE (temporalType = 'POINT' AND startAtMillis >= :rangeStartInclusive AND startAtMillis < :rangeEndExclusive)
           OR (temporalType = 'INTERVAL' AND startAtMillis < :rangeEndExclusive
               AND COALESCE(endAtMillis, 9223372036854775807) > :rangeStartInclusive)
        ORDER BY startAtMillis, id
        """,
    )
    suspend fun getIntersectingWithTags(
        rangeStartInclusive: Long,
        rangeEndExclusive: Long,
    ): List<SessionWithTags>

    @Transaction
    @Query("""
        SELECT * FROM sessions
        WHERE kind = 'ACTIVITY' AND temporalType = 'INTERVAL' AND endAtMillis IS NOT NULL
          AND startAtMillis < :rangeEndExclusive AND endAtMillis > :rangeStartInclusive
        ORDER BY startAtMillis, id
    """)
    suspend fun getCompletedActivityIntervalsInRange(
        rangeStartInclusive: Long,
        rangeEndExclusive: Long,
    ): List<SessionWithTags>

    @Transaction
    @Query("""
        SELECT * FROM sessions
        WHERE kind = 'ACTIVITY' AND (
            (temporalType = 'POINT' AND startAtMillis >= :rangeStartInclusive AND startAtMillis < :rangeEndExclusive)
            OR (temporalType = 'INTERVAL' AND endAtMillis IS NOT NULL
                AND startAtMillis < :rangeEndExclusive AND endAtMillis > :rangeStartInclusive)
        )
        ORDER BY startAtMillis, id
    """)
    suspend fun getActivityFactsInRange(
        rangeStartInclusive: Long,
        rangeEndExclusive: Long,
    ): List<SessionWithTags>

    @Transaction
    @Query("""
        SELECT * FROM sessions
        WHERE kind = 'NAP' AND temporalType = 'INTERVAL'
          AND startAtMillis < :rangeEndExclusive
          AND COALESCE(endAtMillis, 9223372036854775807) > :rangeStartInclusive
        ORDER BY startAtMillis, id
    """)
    suspend fun getNapsInRange(
        rangeStartInclusive: Long,
        rangeEndExclusive: Long,
    ): List<SessionWithTags>

    @Query("SELECT * FROM sessions WHERE kind = 'NIGHT_SLEEP' AND temporalType = 'INTERVAL' ORDER BY startAtMillis, id")
    suspend fun getNightSleeps(): List<SessionEntity>

    @Query(
        """
        SELECT COUNT(*) FROM sessions
        WHERE id != :excludeSessionId
          AND temporalType = 'INTERVAL'
          AND startAtMillis < COALESCE(:newEndExclusive, 9223372036854775807)
          AND COALESCE(endAtMillis, 9223372036854775807) > :newStartInclusive
        """,
    )
    suspend fun countOverlaps(
        newStartInclusive: Long,
        newEndExclusive: Long?,
        excludeSessionId: Long = Long.MIN_VALUE,
    ): Int

    @Query("SELECT * FROM sessions WHERE taskId = :taskId ORDER BY startAtMillis, id")
    suspend fun getForTask(taskId: Long): List<SessionEntity>

    @Query("SELECT id FROM sessions WHERE sourceEventId = :eventId ORDER BY id")
    suspend fun getIdsForSourceEvent(eventId: Long): List<Long>

    @Query(
        """
        UPDATE sessions
        SET sourceEventId = :eventId
        WHERE id IN (:sessionIds)
          AND sourceEventId IS NULL
        """,
    )
    suspend fun restoreSourceEventIfNull(sessionIds: List<Long>, eventId: Long): Int

    @Query(
        """
        UPDATE sessions
        SET endAtMillis = :endAtMillis,
            updatedAtMillis = :updatedAtMillis
        WHERE id = :sessionId AND temporalType = 'INTERVAL' AND endAtMillis IS NULL
        """,
    )
    suspend fun finish(
        sessionId: Long,
        endAtMillis: Long,
        updatedAtMillis: Long,
    )

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    suspend fun deleteById(sessionId: Long): Int

    @Insert
    suspend fun insertTagRefs(refs: List<SessionTagCrossRef>)

    @Query("DELETE FROM session_tag_cross_refs WHERE sessionId = :sessionId")
    suspend fun deleteTagRefs(sessionId: Long)

    @Transaction
    suspend fun replaceTags(sessionId: Long, tagIds: Set<Long>) {
        deleteTagRefs(sessionId)
        if (tagIds.isNotEmpty()) {
            insertTagRefs(tagIds.map { tagId -> SessionTagCrossRef(sessionId, tagId) })
        }
    }
}
