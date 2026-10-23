package io.github.peanutpersimmon.rido.domain.model

data class TaggedSession(
    val session: Session,
    val tagIds: Set<Long> = emptySet(),
)

data class TaggedEvent(
    val event: Event,
    val tagIds: Set<Long> = emptySet(),
)

data class TaggedTask(
    val task: Task,
    val tagIds: Set<Long> = emptySet(),
)

