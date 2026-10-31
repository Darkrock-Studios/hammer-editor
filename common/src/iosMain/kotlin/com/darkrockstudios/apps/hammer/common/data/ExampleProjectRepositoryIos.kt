package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsStore
import com.darkrockstudios.apps.hammer.common.util.toByteArray
import com.darkrockstudios.apps.hammer.common.util.zip.unzipBytesToDirectory
import io.github.aakira.napier.Napier
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import kotlin.time.Clock

actual val exampleProjectModule = module {
	single<ExampleProjectRepositoryiOs>() bind ExampleProjectRepository::class
}

private class ExampleProjectRepositoryiOs(
	globalSettingsStore: GlobalSettingsStore,
	fileSystem: FileSystem,
	toml: Toml,
	clock: Clock,
) : ExampleProjectRepository(globalSettingsStore, fileSystem, toml, clock) {

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

	private fun loadExampleProjectZip(): ByteArray {
		val path = NSBundle.mainBundle.pathForResource(EXAMPLE_PROJECT_FILE_NAME, null)
			?: error("Failed to locate $EXAMPLE_PROJECT_FILE_NAME in main bundle")
		val data = NSData.dataWithContentsOfFile(path)
			?: error("Failed to read $EXAMPLE_PROJECT_FILE_NAME at $path")
		return data.toByteArray()
	}
}
