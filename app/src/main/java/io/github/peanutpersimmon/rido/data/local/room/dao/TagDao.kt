package io.github.peanutpersimmon.rido.data.local.room.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.peanutpersimmon.rido.data.local.room.entity.TagEntity

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY isArchived, sortOrder, name COLLATE NOCASE, id")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<TagEntity>>

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Delete
    suspend fun delete(tag: TagEntity)

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: Long): TagEntity?

    @Query("SELECT * FROM tags WHERE isArchived = 0 ORDER BY sortOrder, name, id")
    suspend fun getActive(): List<TagEntity>

    @Query("SELECT * FROM tags ORDER BY isArchived, sortOrder, name, id")
    suspend fun getAllIncludingArchived(): List<TagEntity>

    @Query("UPDATE tags SET isArchived = 1, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun archive(id: Long, updatedAtMillis: Long)
}
