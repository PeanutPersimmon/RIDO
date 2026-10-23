package io.github.peanutpersimmon.rido.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.repository.RoomTagRepository
import io.github.peanutpersimmon.rido.data.repository.RoomRidoRepository
import io.github.peanutpersimmon.rido.data.local.room.entity.KeywordRuleEntity
import io.github.peanutpersimmon.rido.domain.model.*
import io.github.peanutpersimmon.rido.domain.usecase.InitialClassificationInput
import io.github.peanutpersimmon.rido.domain.usecase.InitialClassificationUseCase
import io.github.peanutpersimmon.rido.domain.service.ActualStatisticsCalculator
import io.github.peanutpersimmon.rido.domain.service.StatisticsGranularity
import io.github.peanutpersimmon.rido.domain.service.StatisticsPeriod
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TagRepositoryTest {
    @Test fun archivedTagStopsRuleOutputButKeepsHistoricalRecordAndStatistics() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext,
            RidoDatabase::class.java).build()
        try {
            val tags = RoomTagRepository(db)
            val tagId = tags.createTags(null, listOf("文献")).single()
            val ruleId = db.keywordRuleDao().insert(KeywordRuleEntity(keyword = "文献",
                createdAtMillis = 1, updatedAtMillis = 1))
            db.keywordRuleDao().replaceTags(ruleId, setOf(tagId))
            val repository = RoomRidoRepository(db)
            val input = InitialClassificationInput("读文献", applyKeywordRules = true)
            assertEquals(setOf(tagId), InitialClassificationUseCase(repository)(input).tagIds)
            val recordId = (repository.createPastSession(Session(title = "读文献", startAtMillis = 100,
                endAtMillis = 200, createdAtMillis = 1, updatedAtMillis = 1), setOf(tagId))
                as DomainResult.Success).value
            tags.deleteTag(tagId)
            assertTrue(InitialClassificationUseCase(repository)(input).tagIds.isEmpty())
            assertEquals(setOf(tagId), db.keywordRuleDao().getWithTags(ruleId)?.tags?.map { it.id }?.toSet())
            assertEquals(setOf(tagId), repository.getSession(recordId)?.tagIds)
            val period = StatisticsPeriod(StatisticsGranularity.DAY, LocalDate.of(1970, 1, 1))
            val sessions = repository.getCompletedActivityIntervalsInRange(0, 86_400_000)
            val stats = (ActualStatisticsCalculator.calculate(sessions, period, ZoneOffset.UTC, 1000)
                as DomainResult.Success).value
            assertEquals(100L, stats.rank(category = false).single { it.id == tagId }.durationMillis)
        } finally { db.close() }
    }
    @Test fun validatesDepthUniquenessOrderAndArchive() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, RidoDatabase::class.java).build()
        try {
            val repository = RoomTagRepository(db)
            val root = repository.createTags(null, listOf("Root")).single()
            val children = repository.createTags(root, listOf("Alpha", "Beta"))
            assertTrue(runCatching { repository.createTags(children[0], listOf("Third level")) }.isFailure)
            assertTrue(runCatching { repository.createTags(root, listOf(" ALPHA ")) }.isFailure)
            assertTrue(runCatching { repository.createTags(root, listOf("New", "new")) }.isFailure)
            assertTrue(runCatching { repository.reorderTags(root, listOf(children[0])) }.isFailure)
            repository.reorderTags(root, children.reversed())
            assertEquals(children.reversed(), db.tagDao().getActive().filter { it.parentId == root }.map { it.id })
            repository.renameTag(children[0], "Renamed")
            assertEquals("Renamed", db.tagDao().getById(children[0])?.name)
            repository.deleteTag(root)
            assertTrue(db.tagDao().getAllIncludingArchived().all { it.isArchived })
            assertTrue(db.tagDao().getActive().isEmpty())
        } finally { db.close() }
    }
}
