package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.base.http.readTomlOrNull
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import io.github.aakira.napier.Napier
import kotlinx.coroutines.withContext
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem

class StatisticsDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val dispatcherIo by injectIoDispatcher()

	suspend fun loadStatistics(): ProjectStatistics? = withContext(dispatcherIo) {
		val file = StatisticsCachePaths.statsFile(projectDef)
		return@withContext if (fileSystem.exists(file)) {
			fileSystem.readTomlOrNull<ProjectStatistics>(file, toml) { e ->
				Napier.e("Failed to load statistics cache", e)
			}
		} else {
			null
		}
	}

	suspend fun saveStatistics(stats: ProjectStatistics) = withContext(dispatcherIo) {
		ensureCacheDirectoryExists()
		val file = StatisticsCachePaths.statsFile(projectDef)
		fileSystem.writeToml(file, toml, stats)
		Napier.d("Statistics saved to cache")
	}

	fun exists(): Boolean {
		return fileSystem.exists(StatisticsCachePaths.statsFile(projectDef))
	}

	suspend fun delete() = withContext(dispatcherIo) {
		val file = StatisticsCachePaths.statsFile(projectDef)
		if (fileSystem.exists(file)) {
			fileSystem.delete(file)
			Napier.d("Statistics cache deleted")
		}
	}

	private fun ensureCacheDirectoryExists() {
		val cacheDir = StatisticsCachePaths.projectCacheDirectory(projectDef)
		if (!fileSystem.exists(cacheDir)) {
			fileSystem.createDirectories(cacheDir)
		}
	}
}
