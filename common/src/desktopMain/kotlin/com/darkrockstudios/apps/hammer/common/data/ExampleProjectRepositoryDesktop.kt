package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.util.zip.unzipBytesToDirectory
import io.github.aakira.napier.Napier
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.InputStream
import kotlin.time.Clock


actual val exampleProjectModule = module {
	singleOf(::ExampleProjectRepositoryDesktop) bind ExampleProjectRepository::class
}

class ExampleProjectRepositoryDesktop(
	globalSettingsRepository: GlobalSettingsRepository,
	fileSystem: FileSystem,
	toml: Toml,
	clock: Clock,
) : ExampleProjectRepository(globalSettingsRepository, fileSystem, toml, clock) {

	private fun loadExampleProjectZip(): ByteArray {
		val path = "/raw/$EXAMPLE_PROJECT_FILE_NAME"
		this::class.java.getResourceAsStream(path).use { inputStream: InputStream? ->
			return inputStream?.readBytes() ?: error("Failed to read example project")
		}
	}

	override fun removeExampleProject() {
		val projectPath = projectsDir() / PROJECT_NAME
		fileSystem.deleteRecursively(projectPath)
	}

	override fun platformInstall() {
		val projectPath = projectsDir() / PROJECT_NAME
		if (!fileSystem.exists(projectPath)) {
			Napier.i("Creating example project")

			val zipBytes = loadExampleProjectZip()
			unzipBytesToDirectory(
				fileSystem = fileSystem,
				zipBytes = zipBytes,
				destinationDirectory = projectsDir()
			)
		} else {
			Napier.i("Skipping example project creation")
		}
	}
}
