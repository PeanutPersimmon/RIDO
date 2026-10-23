package io.github.peanutpersimmon.rido.presentation.voice

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class VoiceAgentDiagnostics(
    val trace: List<String> = emptyList(),
    val rawArguments: String? = null,
    val mappedAction: String? = null,
    val validationResult: String? = null,
) {
    fun asText(): String = buildString {
        appendLine("RIDO Qwen Agent diagnostics (current attempt only)")
        trace.forEach(::appendLine)
        rawArguments?.let { appendLine("rawArguments = $it") }
        mappedAction?.let { appendLine("mappedAction = $it") }
        validationResult?.let { appendLine("validationResult = $it") }
    }
}

/** Memory-only diagnostics. Credentials are never accepted by this API. */
object VoiceAgentDiagnosticsStore {
    private val current = MutableStateFlow(VoiceAgentDiagnostics())
    val state = current.asStateFlow()
    fun begin() { current.value = VoiceAgentDiagnostics() }
    fun event(value: String) { current.update { it.copy(trace = (it.trace + value).takeLast(40)) } }
    fun rawArguments(value: String) { current.update { it.copy(rawArguments = value, mappedAction = null, validationResult = null) } }
    fun mappedAction(value: String) { current.update { it.copy(mappedAction = value) } }
    fun validationResult(value: String) { current.update { it.copy(validationResult = value) } }
}
