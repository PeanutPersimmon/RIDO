package io.github.peanutpersimmon.rido.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import io.github.peanutpersimmon.rido.backup.BackupOperationCoordinator

private val Context.appearanceStore by preferencesDataStore("appearance")

/** Theme preference only. Category data lives in Room. */
class AppearancePreferences(context: Context) {
    private val store = context.applicationContext.appearanceStore
    private val values = store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    val dark = values.map { it[booleanPreferencesKey("night")] ?: false }
    suspend fun setDark(dark: Boolean) = BackupOperationCoordinator.mutation {
        store.edit { it[booleanPreferencesKey("night")] = dark }
    }
}

