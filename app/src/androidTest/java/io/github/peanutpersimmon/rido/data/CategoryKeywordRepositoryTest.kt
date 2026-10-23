package io.github.peanutpersimmon.rido.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.repository.RoomCategoryRepository
import io.github.peanutpersimmon.rido.domain.model.CategoryKeywordConflictException
import io.github.peanutpersimmon.rido.domain.model.ReservedCategoryKeywordException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CategoryKeywordRepositoryTest {
    @Test
    fun batchInsertSucceedsAndUsesDefaultPriority() = runBlocking {
        withRepository { _, repository ->
            val categoryId = repository.createCategory("科研")

            repository.addCategoryKeywords(categoryId, listOf("论文", "实验", "写作"))

            val rules = repository.observeCategoryKeywords(categoryId).first()
            assertEquals(setOf("论文", "实验", "写作"), rules.mapTo(mutableSetOf()) { it.keyword })
            assertTrue(rules.all { it.priority == 0 })
        }
    }

    @Test
    fun batchConflictRollsBackEveryKeyword() = runBlocking {
        withRepository { _, repository ->
            val existing = repository.createCategory("工作")
            repository.addCategoryKeyword(existing, "组会")
            val target = repository.createCategory("科研")

            assertTrue(
                runCatching {
                    repository.addCategoryKeywords(target, listOf("论文", "组会", "实验"))
                }.exceptionOrNull() is CategoryKeywordConflictException,
            )
            assertTrue(repository.observeCategoryKeywords(target).first().isEmpty())
        }
    }

    @Test
    fun batchReservedKeywordRollsBackEveryKeyword() = runBlocking {
        withRepository { _, repository ->
            val categoryId = repository.createCategory("科研")

            assertTrue(
                runCatching {
                    repository.addCategoryKeywords(categoryId, listOf("论文", "午睡", "实验"))
                }.exceptionOrNull() is ReservedCategoryKeywordException,
            )
            assertTrue(repository.observeCategoryKeywords(categoryId).first().isEmpty())
        }
    }

    @Test
    fun normalizedKeywordIsGloballyUniqueAndSelfEditIsAllowed() = runBlocking {
        withRepository { _, repository ->
            val first = repository.createCategory("工作")
            val second = repository.createCategory("学习")
            val ruleId = repository.addCategoryKeyword(first, " ＭＥＥＴＩＮＧ  PLAN ")

            repository.updateCategoryKeyword(first, ruleId, "meeting plan", priority = 2)
            repository.archiveCategory(first)

            assertTrue(
                runCatching { repository.addCategoryKeyword(second, "meeting\tplan") }
                    .exceptionOrNull() is CategoryKeywordConflictException,
            )
        }
    }

    @Test
    fun duplicateActiveKeywordAndSleepVocabularyAreRejected() = runBlocking {
        withRepository { _, repository ->
            val first = repository.createCategory("工作")
            val second = repository.createCategory("学习")
            repository.addCategoryKeyword(first, "组会")

            assertTrue(
                runCatching { repository.addCategoryKeyword(second, "  组会  ") }
                    .exceptionOrNull() is CategoryKeywordConflictException,
            )
            assertTrue(
                runCatching { repository.addCategoryKeyword(second, "午睡") }
                    .exceptionOrNull() is ReservedCategoryKeywordException,
            )
        }
    }

    @Test
    fun archivedRulesAreInactiveWithoutChangingUserEnabledFlag() = runBlocking {
        withRepository { database, repository ->
            val categoryId = repository.createCategory("科研")
            val ruleId = repository.addCategoryKeyword(categoryId, "论文")
            repository.archiveCategory(categoryId)

            assertTrue(database.keywordRuleDao().getEnabledOrdered().isEmpty())
            assertTrue(database.keywordRuleDao().getById(ruleId)?.isEnabled == true)
            assertEquals(1, repository.observeCategoryKeywords(categoryId).first().size)
        }
    }

    @Test
    fun userEditedKeywordDoesNotReturnAfterRename() = runBlocking {
        withRepository { _, repository ->
            val categoryId = repository.createCategory("学习", "purple", listOf("文献", "课程"))
            val rules = repository.observeCategoryKeywords(categoryId).first()
            repository.updateCategoryKeyword(categoryId, rules.first { it.keyword == "文献" }.id,
                "影像组学", 0)
            repository.deleteCategoryKeyword(categoryId, rules.first { it.keyword == "课程" }.id)
            repository.updateCategory(categoryId, "科研", "green", 0)
            repository.addCategoryKeyword(categoryId, "实验")
            assertEquals(setOf("影像组学", "实验"),
                repository.observeCategoryKeywords(categoryId).first().mapTo(mutableSetOf()) { it.keyword })
        }
    }

    private suspend fun withRepository(
        test: suspend (RidoDatabase, RoomCategoryRepository) -> Unit,
    ) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, RidoDatabase::class.java).build()
        try {
            test(database, RoomCategoryRepository(database))
        } finally {
            database.close()
        }
    }
}
