package io.github.peanutpersimmon.rido.ui.components.picker

import androidx.compose.runtime.Composable
import java.time.LocalDate

@Composable
fun AppDatePicker(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    AppDateTimePicker(
        initialDate = initialDate,
        initialTime = null,
        timeOptional = false,
        onDismiss = onDismiss,
        onConfirm = { onConfirm(it.date) },
    )
}
