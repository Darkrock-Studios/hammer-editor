package com.darkrockstudios.apps.hammer.android.widgets

import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.darkrockstudios.apps.hammer.base.ProjectId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class WidgetConfigDataTest {

	private val projectId = ProjectId("0b9d4c1e-5f7a-4e2b-9c3d-1a2b3c4d5e6f")

	@Test
	fun `saved project id and accent read back`() {
		val prefs = emptyPreferences().saveWidgetConfig(7, projectId, "#5A66B5")

		assertEquals(projectId, prefs.getWidgetConfig(7))
		assertEquals("#5A66B5", prefs.getWidgetAccent(7))
	}

	@Test
	fun `project id is stored under the project_id key`() {
		val prefs = emptyPreferences().saveWidgetConfig(7, projectId)

		assertEquals(projectId.id, prefs[stringPreferencesKey("widget:7:project_id")])
	}

	@Test
	fun `saving no project reads back as null`() {
		val prefs = emptyPreferences().saveWidgetConfig(7, null)

		assertNull(prefs.getWidgetConfig(7))
		assertNull(prefs.getWidgetAccent(7))
	}

	@Test
	fun `unconfigured widget reads as null`() {
		assertNull(emptyPreferences().getWidgetConfig(7))
	}

	@Test
	fun `widgets are configured independently`() {
		val other = ProjectId("other")
		val prefs = emptyPreferences()
			.saveWidgetConfig(1, projectId)
			.saveWidgetConfig(2, other)

		assertEquals(projectId, prefs.getWidgetConfig(1))
		assertEquals(other, prefs.getWidgetConfig(2))
	}

	@Test
	fun `pre-4 name config does not resolve and is cleared on save`() {
		val legacyKey = stringPreferencesKey("widget:7")
		val legacy = preferencesOf(legacyKey to "My Story")

		assertNull(legacy.getWidgetConfig(7))

		val saved = legacy.saveWidgetConfig(7, projectId)
		assertFalse(saved.contains(legacyKey))
		assertEquals(projectId, saved.getWidgetConfig(7))
	}
}
