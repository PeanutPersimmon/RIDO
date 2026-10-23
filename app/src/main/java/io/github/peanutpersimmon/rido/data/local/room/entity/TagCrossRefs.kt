package io.github.peanutpersimmon.rido.data.local.room.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "task_tag_cross_refs",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class TaskTagCrossRef(
    val taskId: Long,
    val tagId: Long,
)

@Entity(
    tableName = "event_tag_cross_refs",
    primaryKeys = ["eventId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = EventEntity::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class EventTagCrossRef(
    val eventId: Long,
    val tagId: Long,
)

@Entity(
    tableName = "session_tag_cross_refs",
    primaryKeys = ["sessionId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class SessionTagCrossRef(
    val sessionId: Long,
    val tagId: Long,
)

@Entity(
    tableName = "keyword_rule_tag_cross_refs",
    primaryKeys = ["keywordRuleId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = KeywordRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["keywordRuleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class KeywordRuleTagCrossRef(
    val keywordRuleId: Long,
    val tagId: Long,
)
