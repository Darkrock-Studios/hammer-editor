package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import com.darkrockstudios.apps.hammer.common.util.zip.unzipBytesToDirectory
import io.github.aakira.napier.Napier
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.posix.memcpy
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

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
	val size = length.toInt()
	val out = ByteArray(size)
	if (size == 0) return out
	out.usePinned { pinned ->
		memcpy(pinned.addressOf(0), bytes, length.convert())
	}
	return out
}
