package com.darkrockstudios.apps.hammer.common.data

import com.darkrockstudios.apps.hammer.common.data.globalsettings.GlobalSettingsRepository
import io.github.aakira.napier.Napier
import korlibs.io.file.PathInfo
import korlibs.io.file.combine
import korlibs.io.file.std.applicationDataVfs
import korlibs.io.file.std.openAsZip
import korlibs.io.file.std.resourcesVfs
import kotlinx.coroutines.runBlocking
import okio.FileSystem
import okio.Path.Companion.toPath
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
		val projectPath = globalSettingsRepository.globalSettings.projectsDirectory.toPath()
		fileSystem.deleteRecursively(projectPath)
	}

	override fun platformInstall() {
		// TODO eventually replace with a more targeted solution. Currently this uses Korge Core
		// which is a huge library. Hopefully Okio will implement multiplatform zip handling and
		// we can just use that
		runBlocking {
			val projDir = PathInfo(globalSettingsRepository.globalSettings.projectsDirectory).combine(
				PathInfo(PROJECT_NAME)
			)

			Napier.d("Installing Example to: ${projDir.fullPath}")

			val source = resourcesVfs[EXAMPLE_PROJECT_FILE_NAME].openAsZip()
			val destination = applicationDataVfs[projDir.fullPath]

			source.copyToRecursively(destination)
		}
	}
}
