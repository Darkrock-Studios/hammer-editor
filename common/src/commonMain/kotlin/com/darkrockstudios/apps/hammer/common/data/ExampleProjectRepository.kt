package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import org.koin.core.module.Module


expect val exampleProjectModule: Module

abstract class ExampleProjectRepository(
	protected val globalSettingsRepository: GlobalSettingsRepository
) {
	fun shouldInstallFirstTime(): Boolean =
		!globalSettingsRepository.globalSettings.nux.exampleProjectCreated

	fun install() {
		removeExampleProject()
		platformInstall()
		val updated = globalSettingsRepository.globalSettings.copy(
			nux = globalSettingsRepository.globalSettings.nux.copy(
				exampleProjectCreated = true
			)
		)
		globalSettingsRepository.updateSettings(updated)
	}

	abstract fun removeExampleProject()

	protected abstract fun platformInstall()

	companion object {
		const val PROJECT_NAME = "Alice In Wonderland"
		const val EXAMPLE_PROJECT_FILE_NAME = "alice_in_wonderland_zip"
	}
}