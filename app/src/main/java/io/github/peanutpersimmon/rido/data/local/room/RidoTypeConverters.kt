package io.github.peanutpersimmon.rido.data.local.room

import androidx.room.TypeConverter
import io.github.peanutpersimmon.rido.domain.model.SessionKind
import io.github.peanutpersimmon.rido.domain.model.SessionTemporalType
import io.github.peanutpersimmon.rido.domain.model.TaskCompletionSource
import io.github.peanutpersimmon.rido.domain.model.TaskStatus

class RidoTypeConverters {
    @TypeConverter
    fun taskStatusToStorage(value: TaskStatus): String = value.name

    @TypeConverter
    fun taskStatusFromStorage(value: String): TaskStatus = TaskStatus.valueOf(value)

    @TypeConverter
    fun completionSourceToStorage(value: TaskCompletionSource?): String? = value?.name

    @TypeConverter
    fun completionSourceFromStorage(value: String?): TaskCompletionSource? =
        value?.let(TaskCompletionSource::valueOf)

    @TypeConverter
    fun sessionKindToStorage(value: SessionKind): String = value.name

    @TypeConverter
    fun sessionKindFromStorage(value: String): SessionKind = SessionKind.valueOf(value)

    @TypeConverter
    fun sessionTemporalTypeToStorage(value: SessionTemporalType): String = value.name

    @TypeConverter
    fun sessionTemporalTypeFromStorage(value: String): SessionTemporalType = SessionTemporalType.valueOf(value)
}

