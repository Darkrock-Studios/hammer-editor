package com.darkrockstudios.apps.hammer.wear.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Watch-only preferences. This is the only reader and writer of the wear DataStore. */
interface WearPrefsDatasource {
	val subscribedProjectIds: Flow<Set<String>>
	val lastCaptureProjectId: Flow<String?>

	suspend fun setSubscribed(projectId: String, subscribed: Boolean)
	suspend fun setLastCaptureProjectId(projectId: String)

	/** Points what refers to [oldId] at [newId] in one write; [keepOld] subscribes both. */
	suspend fun replaceProjectId(oldId: String, newId: String, keepOld: Boolean)
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

	override val lastCaptureProjectId: Flow<String?> =
		dataStore.data.map { prefs -> prefs[LAST_CAPTURE_PROJECT_ID] }

	override suspend fun setSubscribed(projectId: String, subscribed: Boolean) {
		dataStore.edit { prefs ->
			val current = prefs[SUBSCRIBED_PROJECT_IDS] ?: emptySet()
			prefs[SUBSCRIBED_PROJECT_IDS] = if (subscribed) current + projectId else current - projectId
		}
	}

	override suspend fun setLastCaptureProjectId(projectId: String) {
		dataStore.edit { prefs -> prefs[LAST_CAPTURE_PROJECT_ID] = projectId }
	}

	override suspend fun replaceProjectId(oldId: String, newId: String, keepOld: Boolean) {
		dataStore.edit { prefs ->
			val subscribed = prefs[SUBSCRIBED_PROJECT_IDS] ?: emptySet()
			if (oldId in subscribed) {
				prefs[SUBSCRIBED_PROJECT_IDS] = if (keepOld) subscribed + newId else subscribed - oldId + newId
			}
			if (!keepOld && prefs[LAST_CAPTURE_PROJECT_ID] == oldId) {
				prefs[LAST_CAPTURE_PROJECT_ID] = newId
			}
		}
	}

	override suspend fun clear() {
		dataStore.edit { it.clear() }
	}

	private companion object {
		val SUBSCRIBED_PROJECT_IDS = stringSetPreferencesKey("subscribed_project_ids")
		val LAST_CAPTURE_PROJECT_ID = stringPreferencesKey("last_capture_project_id")
	}
}
