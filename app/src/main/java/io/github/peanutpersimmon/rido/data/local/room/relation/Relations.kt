package io.github.peanutpersimmon.rido.data.local.room.relation

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation
import io.github.peanutpersimmon.rido.data.local.room.entity.EventEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.EventTagCrossRef
import io.github.peanutpersimmon.rido.data.local.room.entity.KeywordRuleEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.KeywordRuleTagCrossRef
import io.github.peanutpersimmon.rido.data.local.room.entity.SessionEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.SessionTagCrossRef
import io.github.peanutpersimmon.rido.data.local.room.entity.TagEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.TaskEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.TaskTagCrossRef

data class TaskWithTags(
    @Embedded val task: TaskEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TaskTagCrossRef::class,
            parentColumn = "taskId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
)

data class EventWithTags(
    @Embedded val event: EventEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = EventTagCrossRef::class,
            parentColumn = "eventId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
)

data class SessionWithTags(
    @Embedded val session: SessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = SessionTagCrossRef::class,
            parentColumn = "sessionId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
)

data class KeywordRuleWithTags(
    @Embedded val rule: KeywordRuleEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = KeywordRuleTagCrossRef::class,
            parentColumn = "keywordRuleId",
            entityColumn = "tagId",
        ),
    )
    val tags: List<TagEntity>,
)

data class TaskParentLink(
    val id: Long,
    val parentTaskId: Long?,
)
