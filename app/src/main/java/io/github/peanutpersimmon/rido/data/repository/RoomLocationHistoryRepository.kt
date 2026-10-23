package io.github.peanutpersimmon.rido.data.repository

import io.github.peanutpersimmon.rido.data.local.room.RidoDatabase
import io.github.peanutpersimmon.rido.domain.usecase.LocationHistoryRepository

class RoomLocationHistoryRepository(private val database: RidoDatabase) : LocationHistoryRepository {
    override suspend fun recentLocations(): List<String> = database.eventDao().recentLocations()
}

