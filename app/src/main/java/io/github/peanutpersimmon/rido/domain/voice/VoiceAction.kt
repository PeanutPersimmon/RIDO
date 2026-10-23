package io.github.peanutpersimmon.rido.domain.voice

import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import java.time.LocalDate
import java.time.LocalTime

/** Transient values only. KEEP is distinct from an explicitly requested CLEAR. */
sealed interface VoiceFieldPatch<out T> {
    data object Keep : VoiceFieldPatch<Nothing>
    data class Set<T>(val value: T) : VoiceFieldPatch<T>
    data object Clear : VoiceFieldPatch<Nothing>
}

fun <T> VoiceFieldPatch<T>.applyTo(previous: T?): T? = when (this) {
    VoiceFieldPatch.Keep -> previous
    VoiceFieldPatch.Clear -> null
    is VoiceFieldPatch.Set -> value
}

enum class VoiceType { Task, Event, Record }
enum class VoiceEventTemporalType { DATE, POINT, INTERVAL }
enum class VoiceSemanticIntent {
    TaskRequest,
    PlannedArrangement,
    ReportedActivity,
    ReportedPoint,
    StartTimer,
    SleepRecord,
    StartSleep,
    TaskCompletion,
    Unknown,
}
enum class VoiceAction {
    CreateTask, UpdateTask, CompleteTask, UncompleteTask,
    CreateEvent, UpdateEvent, CreateRecord, CreateRecordAndCompleteTask, UpdateRecord,
    StartRecord, StopRecord, StopRecordAndCompleteTask, CreateRecordFromEvent, Clarify,
    CreateSleepRecord, StartSleep,
}

const val MAX_CATEGORY_HINT_LENGTH = 64

data class VoiceFields(
    val title: VoiceFieldPatch<String> = VoiceFieldPatch.Keep,
    val note: VoiceFieldPatch<String> = VoiceFieldPatch.Keep,
    val date: VoiceFieldPatch<LocalDate> = VoiceFieldPatch.Keep,
    val start: VoiceFieldPatch<LocalTime> = VoiceFieldPatch.Keep,
    val endDate: VoiceFieldPatch<LocalDate> = VoiceFieldPatch.Keep,
    val end: VoiceFieldPatch<LocalTime> = VoiceFieldPatch.Keep,
    val dueDate: VoiceFieldPatch<LocalDate> = VoiceFieldPatch.Keep,
    val dueTime: VoiceFieldPatch<LocalTime> = VoiceFieldPatch.Keep,
    val estimatedDuration: VoiceFieldPatch<Int> = VoiceFieldPatch.Keep,
    val completedAt: Long? = null,
    val startAt: Long? = null,
    val endAt: Long? = null,
    val sleepKind: SessionKind? = null,
    val reminderOffsetMinutes: Int? = null,
    val location: VoiceFieldPatch<String> = VoiceFieldPatch.Keep,
    // A name, never a database id; resolution is a separate read-only step.
    val linkedTask: VoiceFieldPatch<String> = VoiceFieldPatch.Keep,
    val parentTask: VoiceFieldPatch<String> = VoiceFieldPatch.Keep,
    /** Model-provided category phrase; never a database id. */
    val categoryHint: String? = null,
)

data class VoiceActionDraft(
    val action: VoiceAction,
    val targetText: String,
    val fields: VoiceFields,
    val originalText: String,
    val nowMillis: Long,
    val hints: List<String> = emptyList(),
    val needsExactTime: Boolean = false,
    val offerExistingTask: Boolean = false,
    val ambiguousStart: Boolean = false,
    val ambiguousEnd: Boolean = false,
    val ambiguousDue: Boolean = false,
    val targetDate: LocalDate? = null,
    val targetStart: LocalTime? = null,
    val targetStartAt: Long? = null,
    val semanticIntent: VoiceSemanticIntent = VoiceSemanticIntent.Unknown,
    val eventTemporalType: VoiceEventTemporalType? = null,
    val recordTemporalType: SessionTemporalType? = null,
    val clarificationReason: VoiceClarification? = null,
    val clarificationMessage: String? = null,
    val clarificationOptions: List<VoiceClarificationOption> = emptyList(),
)

data class VoiceClarificationOption(val type: VoiceType, val draft: VoiceActionDraft)
