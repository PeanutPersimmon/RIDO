package io.github.peanutpersimmon.rido.data.repository

import io.github.peanutpersimmon.rido.backup.withBackupGateTransaction as withTransaction
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.mapper.toDomain
import io.github.peanutpersimmon.rido.data.mapper.toTaggedDomain
import io.github.peanutpersimmon.rido.domain.model.Category
import io.github.peanutpersimmon.rido.domain.model.KeywordRule
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.Tag
import io.github.peanutpersimmon.rido.domain.model.TaggedEvent
import io.github.peanutpersimmon.rido.domain.model.TaggedSession
import io.github.peanutpersimmon.rido.domain.model.TaggedTask
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal val PROJECTION_DATA_CHANGE_TABLES = arrayOf(
    "sessions",
    "events",
    "tasks",
    "categories",
    "tags",
    "session_tag_cross_refs",
    "event_tag_cross_refs",
    "task_tag_cross_refs",
)

internal class RoomProjectionQueries(private val database: RidoDatabase) {
    private val taskDao = database.taskDao()
    private val eventDao = database.eventDao()
    private val sessionDao = database.sessionDao()
    private val categoryDao = database.categoryDao()
    private val tagDao = database.tagDao()
    private val keywordRuleDao = database.keywordRuleDao()

    fun observeDataChanges(): Flow<Unit> = database.invalidationTracker
        .createFlow(*PROJECTION_DATA_CHANGE_TABLES, emitInitialState = true)
        .map { }

    fun observeActiveSession(): Flow<TaggedSession?> = sessionDao.observeActiveWithTags()
        .map { it?.toTaggedDomain() }
        .distinctUntilChanged()

    suspend fun getNightSleeps(): List<Session> = sessionDao.getNightSleeps().map { it.toDomain() }

    suspend fun getSessions(start: Long, end: Long): List<TaggedSession> =
        sessionDao.getIntersectingWithTags(start, end).map { it.toTaggedDomain() }

    suspend fun getCompletedActivityIntervalsInRange(start: Long, end: Long): List<TaggedSession> =
        sessionDao.getCompletedActivityIntervalsInRange(start, end).map { it.toTaggedDomain() }

    suspend fun getActivityFactsInRange(start: Long, end: Long): List<TaggedSession> =
        sessionDao.getActivityFactsInRange(start, end).map { it.toTaggedDomain() }

    suspend fun getNapsInRange(start: Long, end: Long): List<TaggedSession> =
        sessionDao.getNapsInRange(start, end).map { it.toTaggedDomain() }

    suspend fun getEvents(
        firstDay: Long,
        lastDay: Long,
        start: Long,
        end: Long,
    ): List<TaggedEvent> = eventDao.getProjectionCandidatesWithTags(firstDay, lastDay)
        .map { it.toTaggedDomain() }

    suspend fun getEventsByIds(ids: Set<Long>) = eventDao.getByIds(ids).map { it.toDomain() }

    suspend fun getTasks(
        firstDay: Long,
        lastDay: Long,
        start: Long,
        end: Long,
    ): List<TaggedTask> = taskDao.getProjectionCandidatesWithTags(firstDay, lastDay, start, end)
        .map { it.toTaggedDomain() }

    suspend fun getEnabledKeywordRules(): List<KeywordRule> = database.withTransaction {
        val activeTagIds = tagDao.getAllIncludingArchived().asSequence()
            .filterNot { it.isArchived }.map { it.id }.toSet()
        keywordRuleDao.getEnabledOrdered().map { entity ->
            entity.toDomain().let { rule -> rule.copy(tagIds = rule.tagIds.intersect(activeTagIds)) }
        }
    }

    suspend fun getCategories(): List<Category> = categoryDao.getAllIncludingArchived().map { it.toDomain() }

    suspend fun getTags(): List<Tag> = tagDao.getAllIncludingArchived().map { it.toDomain() }
}

