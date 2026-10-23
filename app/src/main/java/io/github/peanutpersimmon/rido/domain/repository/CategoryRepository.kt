package io.github.peanutpersimmon.rido.domain.repository

import io.github.peanutpersimmon.rido.domain.model.Category
import io.github.peanutpersimmon.rido.domain.model.CategoryColorKey
import io.github.peanutpersimmon.rido.domain.model.KeywordRule
import kotlinx.coroutines.flow.Flow

interface CategoryRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun createCategory(
        name: String,
        colorKey: String = CategoryColorKey.NEUTRAL.storageKey,
        keywords: List<String> = emptyList(),
    ): Long
    suspend fun updateCategory(id: Long, name: String, colorKey: String, sortOrder: Int)
    suspend fun archiveCategory(id: Long)
    suspend fun restoreCategory(id: Long)
    suspend fun reorderCategories(ids: List<Long>)
    fun observeCategoryKeywords(categoryId: Long): Flow<List<KeywordRule>>
    suspend fun addCategoryKeyword(categoryId: Long, keyword: String, priority: Int = 0): Long
    suspend fun addCategoryKeywords(categoryId: Long, keywords: List<String>, priority: Int = 0)
    suspend fun updateCategoryKeyword(categoryId: Long, id: Long, keyword: String, priority: Int)
    suspend fun deleteCategoryKeyword(categoryId: Long, id: Long)
}
