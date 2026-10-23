package io.github.peanutpersimmon.rido.data

import io.github.peanutpersimmon.rido.data.mapper.toDomain
import io.github.peanutpersimmon.rido.data.mapper.toEntity
import io.github.peanutpersimmon.rido.domain.model.Task
import org.junit.Assert.*
import org.junit.Test

class TaskLocationMapperTest {
    @Test fun taskLocationRoundTripsAndBlankValuesBecomeNull() {
        val task = Task(id = 7, title = "取报告", location = "  医院  ",
            createdAtMillis = 1, updatedAtMillis = 1)
        val entity = task.toEntity()
        assertEquals("医院", entity.location)
        assertEquals("医院", entity.toDomain().location)
        assertNull(task.copy(location = "   ").toEntity().location)
        assertNull(task.copy(location = "").toEntity().location)
        assertNull(entity.toDomain().copy(location = " ").toEntity().location)
    }
}
