package io.github.peanutpersimmon.rido.presentation.voice

import org.junit.Assert.*
import org.junit.Test

class VoiceAgentDiagnosticsTest {
    @Test fun rawMappingAndValidationStaySeparateForCurrentAttempt() {
        VoiceAgentDiagnosticsStore.begin()
        VoiceAgentDiagnosticsStore.rawArguments("""{"start_at":"2026-09-24T12:00:00+08:00"}""")
        VoiceAgentDiagnosticsStore.mappedAction("CreateEvent")
        VoiceAgentDiagnosticsStore.validationResult("success")
        val text = VoiceAgentDiagnosticsStore.state.value.asText()
        assertTrue(text.contains("rawArguments ="))
        assertTrue(text.contains("mappedAction = CreateEvent"))
        assertTrue(text.contains("validationResult = success"))
        VoiceAgentDiagnosticsStore.begin()
        assertFalse(VoiceAgentDiagnosticsStore.state.value.asText().contains("rawArguments ="))
    }
}
