package com.darkrockstudios.apps.hammer.desktop.update

class FakeUpdatePreferences(
	override var automaticChecks: Boolean = true,
	override var dismissedVersion: String? = null,
) : UpdatePreferences {
	override suspend fun dismiss(version: String) {
		dismissedVersion = version
	}
}
