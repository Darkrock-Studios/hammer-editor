package com.darkrockstudios.apps.hammer.desktop.update

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore

/** The two settings the updater reads, narrowed so the updater can be driven by an in-memory fake. */
interface UpdatePreferences {
	val automaticChecks: Boolean
	val dismissedVersion: String?
	suspend fun dismiss(version: String)
}

class GlobalSettingsUpdatePreferences(private val store: GlobalSettingsStore) : UpdatePreferences {
	override val automaticChecks: Boolean get() = store.globalSettings.automaticUpdateChecks
	override val dismissedVersion: String? get() = store.globalSettings.dismissedUpdateVersion

	override suspend fun dismiss(version: String) {
		store.updateSettings { it.copy(dismissedUpdateVersion = version) }
	}
}
