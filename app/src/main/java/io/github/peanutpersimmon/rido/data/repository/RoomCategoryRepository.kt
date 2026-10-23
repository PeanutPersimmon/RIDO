package io.github.peanutpersimmon.rido.data.repository

import io.github.peanutpersimmon.rido.backup.withBackupGateTransaction as withTransaction
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.local.room.entity.CategoryEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.KeywordRuleEntity
import io.github.peanutpersimmon.rido.data.mapper.toDomain
import io.github.peanutpersimmon.rido.domain.model.ActiveCategoryNameConflictException
import io.github.peanutpersimmon.rido.domain.model.ArchivedCategoryNameConflictException
import io.github.peanutpersimmon.rido.domain.model.ArchivedCategoryKeywordMutationException
import io.github.peanutpersimmon.rido.domain.model.BlankCategoryNameException
import io.github.peanutpersimmon.rido.domain.model.BlankCategoryKeywordException
import io.github.peanutpersimmon.rido.domain.model.CategoryColorKey
import io.github.peanutpersimmon.rido.domain.model.CategoryKeywordConflictException
import io.github.peanutpersimmon.rido.domain.model.CategoryKeywordNotFoundException
import io.github.peanutpersimmon.rido.domain.model.CategoryNotFoundException
import io.github.peanutpersimmon.rido.domain.model.CategoryOrderChangedException
import io.github.peanutpersimmon.rido.domain.model.CategoryRestoreNameConflictException
import io.github.peanutpersimmon.rido.domain.model.InvalidCategoryColorKeyException
import io.github.peanutpersimmon.rido.domain.model.MixedKeywordRuleMutationException
import io.github.peanutpersimmon.rido.domain.model.ReservedCategoryKeywordException
import io.github.peanutpersimmon.rido.domain.repository.CategoryRepository
import io.github.peanutpersimmon.rido.domain.service.CategoryKeywordNormalizer
import io.github.peanutpersimmon.rido.domain.service.ReservedKeywordPolicy
import kotlinx.coroutines.flow.map

class RoomCategoryRepository(private val database: RidoDatabase) : CategoryRepository {
    private val dao = database.categoryDao()
    private val keywordDao = database.keywordRuleDao()

    override fun observeCategories() = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun createCategory(
        name: String,
        colorKey: String,
        keywords: List<String>,
    ): Long = database.withTransaction {
        val normalized = name.trim()
        if (normalized.isEmpty()) throw BlankCategoryNameException()
        requirePaletteColor(colorKey)
        val all = dao.getAllIncludingArchived()
        all.firstOrNull { !it.isArchived && it.name.equals(normalized, ignoreCase = true) }
            ?.let { throw ActiveCategoryNameConflictException(it.id) }
        all.firstOrNull { it.isArchived && it.name.equals(normalized, ignoreCase = true) }
            ?.let { throw ArchivedCategoryNameConflictException(it.id) }
        val now = System.currentTimeMillis()
        val nextOrder = (all.filterNot(CategoryEntity::isArchived).maxOfOrNull(CategoryEntity::sortOrder) ?: -1) + 1
        val id = dao.insert(
            CategoryEntity(
                name = normalized,
                sortOrder = nextOrder,
                createdAtMillis = now,
                updatedAtMillis = now,
                colorKey = colorKey,
            ),
        )
        keywords.forEach { insertCategoryKeyword(id, it, priority = 0) }
        id
    }

    override suspend fun updateCategory(
        id: Long,
        name: String,
        colorKey: String,
        sortOrder: Int,
    ) = database.withTransaction {
        val existing = dao.getById(id) ?: throw CategoryNotFoundException(id)
        val normalized = name.trim()
        if (normalized.isEmpty()) throw BlankCategoryNameException()
        requirePaletteColor(colorKey)
        require(sortOrder >= 0) { "分类顺序无效" }
        if (!existing.isArchived) {
            dao.getActive().firstOrNull {
                it.id != id && it.name.equals(normalized, ignoreCase = true)
            }?.let { throw ActiveCategoryNameConflictException(it.id) }
        }
        dao.update(
            existing.copy(
                name = normalized,
                colorKey = colorKey,
                sortOrder = sortOrder,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun archiveCategory(id: Long) = database.withTransaction {
        val existing = dao.getById(id) ?: throw CategoryNotFoundException(id)
        if (!existing.isArchived) dao.archive(id, System.currentTimeMillis())
    }

    override suspend fun restoreCategory(id: Long) = database.withTransaction {
        val existing = dao.getById(id) ?: throw CategoryNotFoundException(id)
        if (existing.isArchived) {
            dao.getActive().firstOrNull {
                it.name.equals(existing.name, ignoreCase = true)
            }?.let { throw CategoryRestoreNameConflictException(it.id) }
            requireRestorableKeywords(existing.id)
            val nextOrder = (dao.getActive().maxOfOrNull(CategoryEntity::sortOrder) ?: -1) + 1
            dao.update(
                existing.copy(
                    isArchived = false,
                    sortOrder = nextOrder,
                    updatedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun reorderCategories(ids: List<Long>) = database.withTransaction {
        val active = dao.getActive()
        if (ids.size != active.size || ids.toSet() != active.map(CategoryEntity::id).toSet()) {
            throw CategoryOrderChangedException()
        }
        val now = System.currentTimeMillis()
        ids.forEachIndexed { index, id ->
            dao.update(active.first { it.id == id }.copy(sortOrder = index, updatedAtMillis = now))
        }
    }

    override fun observeCategoryKeywords(categoryId: Long) =
        keywordDao.observePureCategoryRules(categoryId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun addCategoryKeyword(
        categoryId: Long,
        keyword: String,
        priority: Int,
    ): Long = database.withTransaction {
        insertCategoryKeyword(categoryId, keyword, priority)
    }

    override suspend fun addCategoryKeywords(
        categoryId: Long,
        keywords: List<String>,
        priority: Int,
    ) = database.withTransaction {
        requireActiveCategory(categoryId)
        if (keywords.isEmpty()) return@withTransaction

        val canonicalKeywords = keywords.map(::requireKeyword)
        canonicalKeywords.forEachIndexed { index, keyword ->
            if (canonicalKeywords.take(index).any { it == keyword }) {
                throw CategoryKeywordConflictException(keyword, categoryId)
            }
            requireKeywordAvailable(keyword)
        }

        val now = System.currentTimeMillis()
        canonicalKeywords.forEach { keyword ->
            keywordDao.insert(
                KeywordRuleEntity(
                    keyword = keyword,
                    categoryId = categoryId,
                    isEnabled = true,
                    priority = priority,
                    createdAtMillis = now,
                    updatedAtMillis = now,
                ),
            )
        }
    }

    override suspend fun updateCategoryKeyword(
        categoryId: Long,
        id: Long,
        keyword: String,
        priority: Int,
    ) = database.withTransaction {
        requireActiveCategory(categoryId)
        val relation = keywordDao.getWithTags(id) ?: throw CategoryKeywordNotFoundException(id)
        if (relation.rule.categoryId != categoryId || relation.tags.isNotEmpty()) {
            throw MixedKeywordRuleMutationException(id)
        }
        val canonical = requireKeyword(keyword)
        requireKeywordAvailable(canonical, excludingRuleId = id)
        keywordDao.update(
            relation.rule.copy(
                keyword = canonical,
                priority = priority,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteCategoryKeyword(categoryId: Long, id: Long) = database.withTransaction {
        requireActiveCategory(categoryId)
        val relation = keywordDao.getWithTags(id) ?: throw CategoryKeywordNotFoundException(id)
        if (relation.rule.categoryId != categoryId || relation.tags.isNotEmpty()) {
            throw MixedKeywordRuleMutationException(id)
        }
        keywordDao.delete(relation.rule)
    }

    private suspend fun insertCategoryKeyword(
        categoryId: Long,
        keyword: String,
        priority: Int,
    ): Long {
        requireActiveCategory(categoryId)
        val canonical = requireKeyword(keyword)
        requireKeywordAvailable(canonical)
        val now = System.currentTimeMillis()
        return keywordDao.insert(
            KeywordRuleEntity(
                keyword = canonical,
                categoryId = categoryId,
                isEnabled = true,
                priority = priority,
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
    }

    private fun requireKeyword(keyword: String): String {
        val canonical = CategoryKeywordNormalizer.normalize(keyword)
        if (canonical.isEmpty()) throw BlankCategoryKeywordException()
        if (ReservedKeywordPolicy.isReserved(canonical)) {
            throw ReservedCategoryKeywordException(canonical)
        }
        return canonical
    }

    private suspend fun requireKeywordAvailable(
        keyword: String,
        excludingRuleId: Long? = null,
    ) {
        findKeywordConflict(keyword, excludingRuleId)?.let { conflict ->
            throw CategoryKeywordConflictException(keyword, conflict.rule.categoryId)
        }
    }

    private suspend fun findKeywordConflict(
        keyword: String,
        excludingRuleId: Long? = null,
    ): io.github.peanutpersimmon.rido.data.local.room.relation.KeywordRuleWithTags? {
        val normalized = CategoryKeywordNormalizer.normalize(keyword)
        return keywordDao.getAllWithTags().firstOrNull { relation ->
            relation.rule.id != excludingRuleId &&
                CategoryKeywordNormalizer.normalize(relation.rule.keyword) == normalized
        }
    }

    private suspend fun requireRestorableKeywords(categoryId: Long) {
        val activeIds = dao.getActive().mapTo(mutableSetOf(), CategoryEntity::id)
        val rules = keywordDao.getAllWithTags()
        val activeKeywords = rules.asSequence()
            .filter { it.rule.categoryId in activeIds }
            .associateBy { CategoryKeywordNormalizer.normalize(it.rule.keyword) }
        rules.asSequence()
            .filter { it.rule.categoryId == categoryId }
            .firstOrNull { CategoryKeywordNormalizer.normalize(it.rule.keyword) in activeKeywords }
            ?.let { restored ->
                val normalized = CategoryKeywordNormalizer.normalize(restored.rule.keyword)
                val conflict = requireNotNull(activeKeywords[normalized])
                throw CategoryKeywordConflictException(
                    restored.rule.keyword,
                    conflict.rule.categoryId,
                )
            }
    }

    private suspend fun requireActiveCategory(categoryId: Long): CategoryEntity {
        val category = dao.getById(categoryId) ?: throw CategoryNotFoundException(categoryId)
        if (category.isArchived) throw ArchivedCategoryKeywordMutationException(categoryId)
        return category
    }

    private fun requirePaletteColor(colorKey: String) {
        if (CategoryColorKey.fromStorage(colorKey) == null) {
            throw InvalidCategoryColorKeyException(colorKey)
        }
    }
}
