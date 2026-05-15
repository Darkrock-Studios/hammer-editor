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
private fun getWidgetAccentKey(widgetId: Int): String = "$WIDGET_CONFIG_PREFIX:$widgetId:accent"

fun Preferences.saveWidgetConfig(
	widgetId: Int,
	projectDef: ProjectDef?,
	accentHex: String? = null,
): MutablePreferences {
	return toMutablePreferences().saveWidgetConfig(widgetId, projectDef, accentHex)
}

fun MutablePreferences.saveWidgetConfig(
	widgetId: Int,
	projectDef: ProjectDef?,
	accentHex: String? = null,
): MutablePreferences {
	this[stringPreferencesKey(getWidgetKey(widgetId))] = projectDef?.name ?: ""
	this[stringPreferencesKey(getWidgetAccentKey(widgetId))] = accentHex ?: ""
	return this
}

fun Preferences.getWidgetConfig(widgetId: Int): String? {
	return this[stringPreferencesKey(getWidgetKey(widgetId))]
}

fun Preferences.getWidgetAccent(widgetId: Int): String? {
	val raw = this[stringPreferencesKey(getWidgetAccentKey(widgetId))]
	return raw?.ifBlank { null }
}
