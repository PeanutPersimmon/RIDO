package io.github.peanutpersimmon.rido.domain.usecase

import io.github.peanutpersimmon.rido.domain.repository.TagRepository
import io.github.peanutpersimmon.rido.domain.repository.CategoryRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class TagManagementUseCases(private val repository: TagRepository) {
    fun observe() = repository.observeTags()
    suspend fun create(parentId: Long?, names: List<String>) = repository.createTags(parentId, names)
    suspend fun rename(id: Long, name: String) = repository.renameTag(id, name)
    suspend fun delete(id: Long) = repository.deleteTag(id)
    suspend fun reorder(parentId: Long?, ids: List<Long>) = repository.reorderTags(parentId, ids)
}
class CategoryUseCases(private val repository: CategoryRepository) {
    fun observe() = repository.observeCategories()
    suspend fun create(
        name: String,
        colorKey: String = io.github.peanutpersimmon.rido.domain.model.CategoryColorKey.NEUTRAL.storageKey,
        keywords: List<String> = emptyList(),
    ) = repository.createCategory(name, colorKey, keywords)
    suspend fun update(id: Long, name: String, colorKey: String, sortOrder: Int) =
        repository.updateCategory(id, name, colorKey, sortOrder)
    suspend fun archive(id: Long) = repository.archiveCategory(id)
    suspend fun restore(id: Long) = repository.restoreCategory(id)
    suspend fun reorder(ids: List<Long>) = repository.reorderCategories(ids)
    fun observeKeywords(categoryId: Long) = repository.observeCategoryKeywords(categoryId)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun observeKeywordsByCategory() = observe().flatMapLatest { categories ->
        if (categories.isEmpty()) {
            flowOf(emptyMap())
        } else {
            combine(categories.map { category ->
                observeKeywords(category.id).map { category.id to it }
            }) { entries -> entries.toMap() }
        }
    }
    suspend fun addKeyword(categoryId: Long, keyword: String, priority: Int = 0) =
        repository.addCategoryKeyword(categoryId, keyword, priority)
    suspend fun addKeywords(categoryId: Long, keywords: List<String>, priority: Int = 0) =
        repository.addCategoryKeywords(categoryId, keywords, priority)
    suspend fun updateKeyword(categoryId: Long, id: Long, keyword: String, priority: Int) =
        repository.updateCategoryKeyword(categoryId, id, keyword, priority)
    suspend fun deleteKeyword(categoryId: Long, id: Long) =
        repository.deleteCategoryKeyword(categoryId, id)
}
