package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectDefaultDispatcher
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.module.Module


expect val exampleProjectModule: Module

abstract class ExampleProjectRepository(
	protected val globalSettingsRepository: GlobalSettingsRepository
) : KoinComponent {
	protected val dispatcherMain by injectMainDispatcher()
	protected val dispatcherDefault by injectDefaultDispatcher()
	private val scope = CoroutineScope(dispatcherDefault)

	fun shouldInstallFirstTime(): Boolean =
		!globalSettingsRepository.globalSettings.nux.exampleProjectCreated

	fun install() {
		removeExampleProject()
		platformInstall()

		scope.launch {
			globalSettingsRepository.updateSettings {
				it.copy(
					nux = globalSettingsRepository.globalSettings.nux.copy(
						exampleProjectCreated = true
					)
				)
			}
		}
	}

	abstract fun removeExampleProject()

	protected abstract fun platformInstall()

	companion object {
		const val PROJECT_NAME = "Alice In Wonderland"
		const val EXAMPLE_PROJECT_FILE_NAME = "alice_in_wonderland_zip"
	}
}