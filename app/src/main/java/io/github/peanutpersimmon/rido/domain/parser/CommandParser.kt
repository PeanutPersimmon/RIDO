package io.github.peanutpersimmon.rido.domain.parser

import java.time.Instant
import java.time.ZoneId

/**
 * Extension boundary for a future RuleBasedParser and optional AiParser.
 * Implementations receive transient text only; the raw utterance must not be persisted long-term.
 */
interface CommandParser {
    suspend fun parse(input: ParserInput): ParserResult
}

data class ParserInput(
    val commandId: String,
    val transientText: String,
    val receivedAt: Instant,
    val zoneId: ZoneId,
)

sealed interface ParserResult {
    data class Parsed(val draft: CreateRecordDraft) : ParserResult
    data class NotUnderstood(val safeMessage: String) : ParserResult
}

data class CreateRecordDraft(
    val commandId: String,
    val type: RecordType,
    val title: String,
)

enum class RecordType { Task, Event, Session }

