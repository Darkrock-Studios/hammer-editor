package com.darkrockstudios.apps.hammer.common.components.projectselection.accountsettings

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.getAndUpdate
import com.darkrockstudios.apps.hammer.common.components.SavableComponent
import com.darkrockstudios.apps.hammer.common.components.savableState
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.data.migrator.DataMigrator
import com.darkrockstudios.apps.hammer.common.data.projectsrepository.ProjectsRepository
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectMainDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.HPath
import com.darkrockstudios.apps.hammer.common.fileio.okio.toHPath
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import org.koin.core.component.get
import org.koin.core.component.inject

class DesktopPlatformSettingsComponent(componentContext: ComponentContext) : DesktopPlatformSettings,
	SavableComponent<DesktopPlatformSettings.PlatformState>(componentContext) {

	private val mainDispatcher by injectMainDispatcher()

	private val globalSettingsRepository: GlobalSettingsRepository by inject()
	private val projectsRepository: ProjectsRepository by inject()

	private val _state by savableState {
		DesktopPlatformSettings.PlatformState(
			projectsDir = projectsRepository.getProjectsDirectory(),
		)
	}

	override val state: Value<DesktopPlatformSettings.PlatformState> = _state
	override fun getStateSerializer() = DesktopPlatformSettings.PlatformState.serializer()


	init {
		watchSettingsUpdates()
	}

	private fun watchSettingsUpdates() {
		scope.launch {
			globalSettingsRepository.globalSettingsUpdates.collect { settings ->
				withContext(dispatcherMain) {
					_state.getAndUpdate {
						val projectsPath = settings.projectsDirectory.toPath().toHPath()
						it.copy(
							projectsDir = projectsPath,
						)
					}
				}
			}
		}
	}

	override fun setProjectsDir(path: String) {
		val hpath = HPath(
			path = path,
			name = "",
			isAbsolute = true
		)

		scope.launch {
			globalSettingsRepository.updateSettings {
				it.copy(
					projectsDirectory = path
				)
			}

			projectsRepository.ensureProjectDirectory()

			// Migrate the new project directory if needed
			val dataMigrator: DataMigrator = get<DataMigrator>()
			dataMigrator.handleDataMigration()

			withContext(mainDispatcher) {
				_state.getAndUpdate {
					it.copy(projectsDir = hpath)
				}
			}
		}
	}
}
