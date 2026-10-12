package com.darkrockstudios.apps.hammer.android.widgets

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.darkrockstudios.apps.hammer.base.ProjectId
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.java.KoinJavaComponent.getKoin

private const val WIDGET_PREFERENCES_NAME = "widget_config"

val Context.widgetConfigDataStore by preferencesDataStore(
	name = WIDGET_PREFERENCES_NAME,
)

private const val WIDGET_CONFIG_PREFIX = "widget"

// Pre-4.0 configs stored the project name under this key. It is never read, only cleared on save.
private fun getLegacyProjectNameKey(widgetId: Int): String = "$WIDGET_CONFIG_PREFIX:$widgetId"
private fun getWidgetProjectIdKey(widgetId: Int): String = "$WIDGET_CONFIG_PREFIX:$widgetId:project_id"
private fun getWidgetAccentKey(widgetId: Int): String = "$WIDGET_CONFIG_PREFIX:$widgetId:accent"

fun Preferences.saveWidgetConfig(
	widgetId: Int,
	projectId: ProjectId?,
	accentHex: String? = null,
): MutablePreferences {
	return toMutablePreferences().saveWidgetConfig(widgetId, projectId, accentHex)
}

fun MutablePreferences.saveWidgetConfig(
	widgetId: Int,
	projectId: ProjectId?,
	accentHex: String? = null,
): MutablePreferences {
	this[stringPreferencesKey(getWidgetProjectIdKey(widgetId))] = projectId?.id ?: ""
	this[stringPreferencesKey(getWidgetAccentKey(widgetId))] = accentHex ?: ""
	remove(stringPreferencesKey(getLegacyProjectNameKey(widgetId)))
	return this
}

fun Preferences.getWidgetConfig(widgetId: Int): ProjectId? {
	val raw = this[stringPreferencesKey(getWidgetProjectIdKey(widgetId))]
	return raw?.ifBlank { null }?.let(::ProjectId)
}

fun Preferences.getWidgetAccent(widgetId: Int): String? {
	val raw = this[stringPreferencesKey(getWidgetAccentKey(widgetId))]
	return raw?.ifBlank { null }
}

/** Null when [projectId] is blank or no local project has it. */
internal suspend fun findWidgetProject(projectId: String?): ProjectDef? {
	if (projectId.isNullOrBlank()) return null
	return withContext(Dispatchers.IO) {
		runCatching { getKoin().get<ProjectsRepository>().findProject(ProjectId(projectId)) }.getOrNull()
	}
}
