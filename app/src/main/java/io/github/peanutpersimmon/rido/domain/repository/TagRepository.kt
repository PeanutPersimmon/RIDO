package io.github.peanutpersimmon.rido.domain.repository

import io.github.peanutpersimmon.rido.domain.model.Tag
import kotlinx.coroutines.flow.Flow

interface TagRepository {
    fun observeTags(): Flow<List<Tag>>
    suspend fun createTags(parentId: Long?, names: List<String>): List<Long>
    suspend fun renameTag(id: Long, name: String)
    suspend fun deleteTag(id: Long)
    suspend fun reorderTags(parentId: Long?, ids: List<Long>)
}
