package io.github.peanutpersimmon.rido.data.mapper

import io.github.peanutpersimmon.rido.data.local.room.entity.CategoryEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.EventEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.KeywordRuleEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.ProcessedCommandEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.SessionEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.TagEntity
import io.github.peanutpersimmon.rido.data.local.room.entity.TaskEntity
import io.github.peanutpersimmon.rido.data.local.room.relation.KeywordRuleWithTags
import io.github.peanutpersimmon.rido.data.local.room.relation.EventWithTags
import io.github.peanutpersimmon.rido.data.local.room.relation.SessionWithTags
import io.github.peanutpersimmon.rido.data.local.room.relation.TaskWithTags
import io.github.peanutpersimmon.rido.domain.model.Category
import io.github.peanutpersimmon.rido.domain.model.Event
import io.github.peanutpersimmon.rido.domain.model.KeywordRule
import io.github.peanutpersimmon.rido.domain.model.ProcessedCommand
import io.github.peanutpersimmon.rido.domain.model.Session
import io.github.peanutpersimmon.rido.domain.model.Tag
import io.github.peanutpersimmon.rido.domain.model.Task
import io.github.peanutpersimmon.rido.domain.model.TaggedEvent
import io.github.peanutpersimmon.rido.domain.model.TaggedSession
import io.github.peanutpersimmon.rido.domain.model.TaggedTask
import io.github.peanutpersimmon.rido.domain.validation.DataValidator

fun TaskEntity.toDomain(): Task = Task(
    id = id,
    title = title,
    note = note,
    location = location,
    status = status,
    parentTaskId = parentTaskId,
    plannedDateEpochDay = plannedDateEpochDay,
    plannedStartMinuteOfDay = plannedStartMinuteOfDay,
    plannedReminderOffsetMinutes = plannedReminderOffsetMinutes,
    dueDateEpochDay = dueDateEpochDay,
    dueMinuteOfDay = dueMinuteOfDay,
    dueReminderOffsetMinutes = dueReminderOffsetMinutes,
    estimatedDurationMinutes = estimatedDurationMinutes,
    categoryId = categoryId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    completedAtMillis = completedAtMillis,
    cancelledAtMillis = cancelledAtMillis,
    completionSource = completionSource,
)

fun Task.toEntity(): TaskEntity = TaskEntity(
    id = id,
    title = title.trim(),
    note = note?.trim()?.takeIf(String::isNotEmpty),
    location = DataValidator.normalizedTaskLocation(location),
    status = status,
    parentTaskId = parentTaskId,
    plannedDateEpochDay = plannedDateEpochDay,
    plannedStartMinuteOfDay = plannedStartMinuteOfDay,
    plannedReminderOffsetMinutes = plannedReminderOffsetMinutes,
    dueDateEpochDay = dueDateEpochDay,
    dueMinuteOfDay = dueMinuteOfDay,
    dueReminderOffsetMinutes = dueReminderOffsetMinutes,
    estimatedDurationMinutes = estimatedDurationMinutes,
    categoryId = categoryId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    completedAtMillis = completedAtMillis,
    cancelledAtMillis = cancelledAtMillis,
    completionSource = completionSource,
)

fun EventEntity.toDomain(): Event = Event(
    id = id,
    title = title,
    note = note,
    location = location,
    dateEpochDay = dateEpochDay,
    startMinuteOfDay = startMinuteOfDay,
    startReminderOffsetMinutes = startReminderOffsetMinutes,
    endDateEpochDay = endDateEpochDay,
    endMinuteOfDay = endMinuteOfDay,
    categoryId = categoryId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun Event.toEntity(): EventEntity = EventEntity(
    id = id,
    title = title.trim(),
    note = note?.trim()?.takeIf(String::isNotEmpty),
    location = location?.trim()?.takeIf(String::isNotEmpty),
    dateEpochDay = dateEpochDay,
    startMinuteOfDay = startMinuteOfDay,
    startReminderOffsetMinutes = startReminderOffsetMinutes,
    endDateEpochDay = endDateEpochDay,
    endMinuteOfDay = endMinuteOfDay,
    categoryId = categoryId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun SessionEntity.toDomain(): Session = Session(
    id = id,
    title = title,
    note = note,
    location = location,
    kind = kind,
    temporalType = temporalType,
    startAtMillis = startAtMillis,
    endAtMillis = endAtMillis,
    taskId = taskId,
    sourceEventId = sourceEventId,
    categoryId = categoryId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun Session.toEntity(): SessionEntity = SessionEntity(
    id = id,
    title = title.trim(),
    note = note?.trim()?.takeIf(String::isNotEmpty),
    location = location?.trim()?.takeIf(String::isNotEmpty),
    kind = kind,
    temporalType = temporalType,
    startAtMillis = startAtMillis,
    endAtMillis = endAtMillis,
    taskId = taskId,
    sourceEventId = sourceEventId,
    categoryId = categoryId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    colorKey = colorKey,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name.trim(),
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    colorKey = colorKey,
)

fun TagEntity.toDomain(): Tag = Tag(id, name, isArchived, sortOrder, createdAtMillis, updatedAtMillis, parentId)

fun Tag.toEntity(): TagEntity = TagEntity(
    id = id,
    name = name.trim(),
    isArchived = isArchived,
    sortOrder = sortOrder,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    parentId = parentId,
)

fun KeywordRuleWithTags.toDomain(): KeywordRule = KeywordRule(
    id = rule.id,
    keyword = rule.keyword,
    categoryId = rule.categoryId,
    tagIds = tags.mapTo(mutableSetOf()) { it.id },
    isEnabled = rule.isEnabled,
    priority = rule.priority,
    createdAtMillis = rule.createdAtMillis,
    updatedAtMillis = rule.updatedAtMillis,
)

fun KeywordRule.toEntity(): KeywordRuleEntity = KeywordRuleEntity(
    id = id,
    keyword = keyword.trim(),
    categoryId = categoryId,
    isEnabled = isEnabled,
    priority = priority,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun ProcessedCommandEntity.toDomain(): ProcessedCommand = ProcessedCommand(commandId, processedAtMillis)

fun ProcessedCommand.toEntity(): ProcessedCommandEntity = ProcessedCommandEntity(commandId.trim(), processedAtMillis)

fun TaskWithTags.toTaggedDomain(): TaggedTask = TaggedTask(
    task = task.toDomain(),
    tagIds = tags.mapTo(mutableSetOf()) { it.id },
)

fun EventWithTags.toTaggedDomain(): TaggedEvent = TaggedEvent(
    event = event.toDomain(),
    tagIds = tags.mapTo(mutableSetOf()) { it.id },
)

fun SessionWithTags.toTaggedDomain(): TaggedSession = TaggedSession(
    session = session.toDomain(),
    tagIds = tags.mapTo(mutableSetOf()) { it.id },
)
