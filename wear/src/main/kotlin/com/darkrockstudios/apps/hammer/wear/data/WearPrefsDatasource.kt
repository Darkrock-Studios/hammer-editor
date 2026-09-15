package com.darkrockstudios.apps.hammer.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Watch-only preferences. This is the only reader and writer of the wear DataStore. */
interface WearPrefsDatasource {
	val subscribedProjectIds: Flow<Set<String>>

	suspend fun setSubscribed(projectId: String, subscribed: Boolean)
	suspend fun clear()
}

private val Context.wearPrefsDataStore by preferencesDataStore(name = "wear_prefs")

fun createWearPrefsDatasource(context: Context): WearPrefsDatasource =
	DataStoreWearPrefsDatasource(context.wearPrefsDataStore)

private class DataStoreWearPrefsDatasource(
	private val dataStore: DataStore<Preferences>,
) : WearPrefsDatasource {

	override val subscribedProjectIds: Flow<Set<String>> =
		dataStore.data.map { prefs -> prefs[SUBSCRIBED_PROJECT_IDS] ?: emptySet() }

	override suspend fun setSubscribed(projectId: String, subscribed: Boolean) {
		dataStore.edit { prefs ->
			val current = prefs[SUBSCRIBED_PROJECT_IDS] ?: emptySet()
			prefs[SUBSCRIBED_PROJECT_IDS] = if (subscribed) current + projectId else current - projectId
		}
	}

	override suspend fun clear() {
		dataStore.edit { it.clear() }
	}

	private companion object {
		val SUBSCRIBED_PROJECT_IDS = stringSetPreferencesKey("subscribed_project_ids")
	}
}
