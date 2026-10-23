package io.github.peanutpersimmon.rido.data.local.room.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.peanutpersimmon.rido.data.local.room.entity.CategoryEntity

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY isArchived, sortOrder, name, id")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<CategoryEntity>>

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT * FROM categories WHERE isArchived = 0 ORDER BY sortOrder, name, id")
    suspend fun getActive(): List<CategoryEntity>

    @Query("SELECT * FROM categories ORDER BY isArchived, sortOrder, name, id")
    suspend fun getAllIncludingArchived(): List<CategoryEntity>

    @Query("UPDATE categories SET isArchived = 1, updatedAtMillis = :updatedAtMillis WHERE id = :id")
    suspend fun archive(id: Long, updatedAtMillis: Long)

}
