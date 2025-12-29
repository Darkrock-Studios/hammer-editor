package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.util.zip.unzipBytesToDirectory
import io.github.aakira.napier.Napier
import okio.FileSystem
import okio.Path.Companion.toPath
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import java.io.InputStream

actual val exampleProjectModule = module {
	singleOf(::ExampleProjectRepositoryAndroid) bind ExampleProjectRepository::class
}

class ExampleProjectRepositoryAndroid(
	globalSettingsRepository: GlobalSettingsRepository,
	private val fileSystem: FileSystem
) : ExampleProjectRepository(globalSettingsRepository) {

	private fun loadExampleProjectZip(): ByteArray {
		val path = "/raw/$EXAMPLE_PROJECT_FILE_NAME"
		this::class.java.getResourceAsStream(path).use { inputStream: InputStream? ->
			return inputStream?.readBytes() ?: error("Failed to read example project from $path")
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

	private fun projectsDir() = globalSettingsRepository.globalSettings.projectsDirectory.toPath()

	companion object {
		private const val PROJECT_NAME = "Alice In Wonderland"
	}
}