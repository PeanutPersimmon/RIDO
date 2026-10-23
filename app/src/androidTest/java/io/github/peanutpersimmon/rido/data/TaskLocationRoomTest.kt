package io.github.peanutpersimmon.rido.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.data.mapper.toDomain
import io.github.peanutpersimmon.rido.data.mapper.toEntity
import io.github.peanutpersimmon.rido.domain.model.Task
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskLocationRoomTest {
    @Test fun createReloadEditClearAndRecentLocations() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val room = Room.inMemoryDatabaseBuilder(context, RidoDatabase::class.java).build()
        try {
            val dao = room.taskDao()
            val id = dao.insert(Task(title = "取报告", location = " 医院 ",
                createdAtMillis = 1, updatedAtMillis = 1).toEntity())
            assertEquals("医院", dao.getById(id)!!.toDomain().location)
            assertEquals("医院", room.eventDao().recentLocations().first())

            dao.update(dao.getById(id)!!.toDomain().copy(location = "实验室", updatedAtMillis = 2).toEntity())
            assertEquals("实验室", dao.getById(id)!!.toDomain().location)
            dao.update(dao.getById(id)!!.toDomain().copy(location = "   ", updatedAtMillis = 3).toEntity())
            assertNull(dao.getById(id)!!.toDomain().location)
        } finally { room.close() }
    }
}
