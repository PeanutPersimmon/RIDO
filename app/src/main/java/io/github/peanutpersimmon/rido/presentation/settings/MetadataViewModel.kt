package io.github.peanutpersimmon.rido.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.peanutpersimmon.rido.domain.model.Tag
import io.github.peanutpersimmon.rido.domain.model.Category
import io.github.peanutpersimmon.rido.domain.model.CategoryColorKey
import io.github.peanutpersimmon.rido.domain.model.KeywordRule
import io.github.peanutpersimmon.rido.domain.usecase.TagManagementUseCases
import io.github.peanutpersimmon.rido.domain.usecase.CategoryUseCases
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MetadataUiState(
    val tags: List<Tag> = emptyList(),
    val categories: List<Category> = emptyList(),
    val categoryKeywords: Map<Long, List<KeywordRule>> = emptyMap(),
    val error: String? = null,
    val message: String? = null,
)

class MetadataViewModel(
    private val tags: TagManagementUseCases,
    private val categories: CategoryUseCases,
    private val getRecentLocations: io.github.peanutpersimmon.rido.domain.usecase.GetRecentLocationsUseCase? = null,
) : ViewModel() {
    private val _recentLocations = MutableStateFlow<List<String>>(emptyList())
    val recentLocations = _recentLocations.asStateFlow()
    fun refreshRecentLocations() = viewModelScope.launch {
        try { _recentLocations.value = getRecentLocations?.invoke().orEmpty() }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { _recentLocations.value = emptyList() }
    }
    private val error = MutableStateFlow<String?>(null)
    private val message = MutableStateFlow<String?>(null)
    val uiState = combine(
        tags.observe(),
        categories.observe(),
        categories.observeKeywordsByCategory(),
        error,
        message,
    ) { t, c, keywords, e, message ->
        MetadataUiState(t, c, keywords, e, message)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetadataUiState())

    fun createTags(parentId: Long?, names: List<String>, onDone: (List<Long>) -> Unit = {}) =
        runOperation { onDone(tags.create(parentId, names)) }
    fun renameTag(id: Long, name: String, onDone: () -> Unit = {}) =
        runOperation { tags.rename(id, name); onDone() }
    fun deleteTag(id: Long, onDone: () -> Unit = {}) = runOperation { tags.delete(id); onDone() }
    fun reorderTags(parentId: Long?, ids: List<Long>) = runOperation { tags.reorder(parentId, ids) }
    fun createCategory(
        name: String,
        colorKey: String = CategoryColorKey.NEUTRAL.storageKey,
        keywords: List<String> = emptyList(),
        onDone: (Long) -> Unit,
    ) = runOperation { onDone(categories.create(name, colorKey, keywords)) }
    fun updateCategory(id: Long, name: String, colorKey: String, sortOrder: Int, onDone: () -> Unit = {}) =
        runOperation { categories.update(id, name, colorKey, sortOrder); onDone() }
    fun archiveCategory(id: Long, onDone: () -> Unit = {}) =
        runOperation { categories.archive(id); onDone() }
    fun restoreCategory(id: Long, onDone: () -> Unit = {}) =
        runOperation { categories.restore(id); onDone() }
    fun reorderCategories(ids: List<Long>) = runOperation { categories.reorder(ids) }
    fun addCategoryKeyword(categoryId: Long, keyword: String, priority: Int = 0, onDone: () -> Unit = {}) =
        runOperation { categories.addKeyword(categoryId, keyword, priority); onDone() }
    fun addCategoryKeywords(
        categoryId: Long,
        keywords: List<String>,
        priority: Int = 0,
        onDone: () -> Unit = {},
    ) = runOperation {
        categories.addKeywords(categoryId, keywords, priority)
        onDone()
    }
    fun updateCategoryKeyword(
        categoryId: Long,
        id: Long,
        keyword: String,
        priority: Int,
        onDone: () -> Unit = {},
    ) = runOperation { categories.updateKeyword(categoryId, id, keyword, priority); onDone() }
    fun deleteCategoryKeyword(categoryId: Long, id: Long, onDone: () -> Unit = {}) =
        runOperation { categories.deleteKeyword(categoryId, id); onDone() }
    fun clearError() { error.value = null; message.value = null }
    private fun runOperation(action: suspend () -> Unit) = viewModelScope.launch {
        error.value = null
        message.value = null
        try { action() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { error.value = e.message ?: "操作失败，请重试" }
    }
    companion object {
        fun factory(tags: TagManagementUseCases, categories: CategoryUseCases,
            recentLocations: io.github.peanutpersimmon.rido.domain.usecase.GetRecentLocationsUseCase? = null) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    MetadataViewModel(tags, categories, recentLocations) as T
            }
    }
}
