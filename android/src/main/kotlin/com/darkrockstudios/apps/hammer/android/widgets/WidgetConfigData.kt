package com.darkrockstudios.apps.hammer.android.widgets

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.darkrockstudios.apps.hammer.common.data.ProjectDef

private const val WIDGET_PREFERENCES_NAME = "widget_config"

val Context.widgetConfigDataStore by preferencesDataStore(
	name = WIDGET_PREFERENCES_NAME,
)

private const val WIDGET_CONFIG_PREFIX = "widget"
private fun getWidgetKey(widgetId: Int): String = "$WIDGET_CONFIG_PREFIX:$widgetId"

fun Preferences.saveWidgetConfig(widgetId: Int, projectDef: ProjectDef?): MutablePreferences {
	return toMutablePreferences().saveWidgetConfig(widgetId, projectDef)
}

fun MutablePreferences.saveWidgetConfig(
	widgetId: Int,
	projectDef: ProjectDef?
): MutablePreferences {
	val configValue = projectDef?.name ?: ""
	val key = getWidgetKey(widgetId)
	this[stringPreferencesKey(key)] = configValue

	return this
}

fun Preferences.getWidgetConfig(widgetId: Int): String? {
	val key = getWidgetKey(widgetId)
	return this[stringPreferencesKey(key)]
}