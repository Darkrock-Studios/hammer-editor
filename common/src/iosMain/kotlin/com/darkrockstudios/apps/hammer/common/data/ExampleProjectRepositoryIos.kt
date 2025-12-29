package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.util.zip.unzipBytesToDirectory
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

actual val exampleProjectModule = module {
	singleOf(::ExampleProjectRepositoryiOs) bind ExampleProjectRepository::class
}

private class ExampleProjectRepositoryiOs(
	globalSettingsRepository: GlobalSettingsRepository,
	private val fileSystem: FileSystem,
) : ExampleProjectRepository(globalSettingsRepository) {

	override fun removeExampleProject() {
		val projectPath = projectsDir() / PROJECT_NAME
		fileSystem.deleteRecursively(projectPath)
	}

	@OptIn(ExperimentalResourceApi::class)
	override fun platformInstall() {
		val projectPath = projectsDir() / PROJECT_NAME
		if (!fileSystem.exists(projectPath)) {
			Napier.i("Creating example project")

			runBlocking {
				val zipBytes = Res.readBytes("raw/$EXAMPLE_PROJECT_FILE_NAME")
				unzipBytesToDirectory(
					fileSystem = fileSystem,
					zipBytes = zipBytes,
					destinationDirectory = projectsDir()
				)
			}
		} else {
			Napier.i("Skipping example project creation")
		}
	}

	private fun projectsDir() = globalSettingsRepository.globalSettings.projectsDirectory.toPath()
}
