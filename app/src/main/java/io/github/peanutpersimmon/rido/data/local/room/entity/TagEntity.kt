package io.github.peanutpersimmon.rido.data.local.room.entity

import androidx.room.ForeignKey
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tags",
    indices = [Index(value = ["isArchived", "sortOrder"]), Index(value = ["parentId"])],
    foreignKeys = [ForeignKey(entity = TagEntity::class, parentColumns = ["id"], childColumns = ["parentId"], onDelete = ForeignKey.SET_NULL)],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val parentId: Long? = null,
)
