package io.github.peanutpersimmon.rido.data.repository

import io.github.peanutpersimmon.rido.backup.withBackupGateTransaction as withTransaction
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.local.room.entity.TagEntity
import io.github.peanutpersimmon.rido.data.mapper.toDomain
import io.github.peanutpersimmon.rido.domain.repository.TagRepository
import kotlinx.coroutines.flow.map

/** Adapted from MyLibrary OfflineTagRepository: two levels, sibling uniqueness, atomic order. */
class RoomTagRepository(private val database: RidoDatabase) : TagRepository {
    private val dao = database.tagDao()
    override fun observeTags() = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun createTags(parentId: Long?, names: List<String>): List<Long> = database.withTransaction {
        validateParent(parentId)
        val normalized = names.map(String::trim)
        require(normalized.isNotEmpty() && normalized.none(String::isBlank)) { "标签名称不能为空" }
        require(normalized.indices.all { i -> normalized.take(i).none { it.equals(normalized[i], true) } }) { "存在重复标签" }
        val siblings = dao.getActive().filter { it.parentId == parentId }
        require(normalized.none { name -> siblings.any { it.name.equals(name, true) } }) { "同级标签名称已存在" }
        val firstOrder = (siblings.maxOfOrNull { it.sortOrder } ?: -1) + 1
        val now = System.currentTimeMillis()
        normalized.mapIndexed { index, name ->
            dao.insert(TagEntity(name = name, parentId = parentId, sortOrder = firstOrder + index,
                createdAtMillis = now, updatedAtMillis = now))
        }
    }

    override suspend fun renameTag(id: Long, name: String) = database.withTransaction {
        val tag = requireNotNull(dao.getById(id)) { "标签不存在" }
        val normalized = name.trim()
        require(normalized.isNotEmpty()) { "标签名称不能为空" }
        require(dao.getActive().none { it.id != id && it.parentId == tag.parentId && it.name.equals(normalized, true) }) {
            "同级标签名称已存在"
        }
        dao.update(tag.copy(name = normalized, updatedAtMillis = System.currentTimeMillis()))
    }

    override suspend fun deleteTag(id: Long) = database.withTransaction {
        val tag = requireNotNull(dao.getById(id)) { "标签不存在" }
        // RIDO retains history: deletion removes the option, not historical references.
        val now = System.currentTimeMillis()
        if (tag.parentId == null) dao.getAllIncludingArchived().filter { it.parentId == id }.forEach { dao.archive(it.id, now) }
        dao.archive(id, now)
    }

    override suspend fun reorderTags(parentId: Long?, ids: List<Long>) = database.withTransaction {
        validateParent(parentId)
        val siblings = dao.getActive().filter { it.parentId == parentId }
        require(ids.size == siblings.size && ids.toSet() == siblings.map { it.id }.toSet()) { "标签已变化，请重试" }
        val now = System.currentTimeMillis()
        ids.forEachIndexed { index, id -> dao.update(siblings.first { it.id == id }.copy(sortOrder = index, updatedAtMillis = now)) }
    }

    private suspend fun validateParent(parentId: Long?) {
        if (parentId == null) return
        val root = requireNotNull(dao.getById(parentId)) { "一级标签不存在" }
        require(root.parentId == null && !root.isArchived) { "只支持两级标签，且一级标签必须可用" }
    }
}
