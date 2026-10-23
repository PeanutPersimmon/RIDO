package io.github.peanutpersimmon.rido.data.local.room.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import io.github.peanutpersimmon.rido.domain.model.CategoryColorKey

@Entity(
    tableName = "categories",
    indices = [Index(value = ["isArchived", "sortOrder"])],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    @ColumnInfo(defaultValue = "'neutral'")
    val colorKey: String = CategoryColorKey.NEUTRAL.storageKey,
)

