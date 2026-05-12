package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.Res
import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.util.zip.unzipBytesToDirectory
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import kotlin.time.Clock

actual val exampleProjectModule = module {
	singleOf(::ExampleProjectRepositoryiOs) bind ExampleProjectRepository::class
}

private class ExampleProjectRepositoryiOs(
	globalSettingsRepository: GlobalSettingsRepository,
	fileSystem: FileSystem,
	toml: Toml,
	clock: Clock,
) : ExampleProjectRepository(globalSettingsRepository, fileSystem, toml, clock) {

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
}
