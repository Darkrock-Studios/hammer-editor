package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import co.touchlab.kermit.Logger
import com.darkrockstudios.apps.hammer.base.http.readTomlOrNull
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.okio.isWithin
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
		readProjectStatistics(projectDef, fileSystem, toml)
	}

	suspend fun saveStatistics(stats: ProjectStatistics) = withContext(dispatcherIo) {
		ensureCacheDirectoryExists()
		val file = StatisticsCachePaths.statsFile(projectDef)
		requireWithinCacheRoot(file)
		fileSystem.writeToml(file, toml, stats)
		Logger.d("Statistics saved to cache")
	}

	fun exists(): Boolean {
		return fileSystem.exists(StatisticsCachePaths.statsFile(projectDef))
	}

	suspend fun delete() = withContext(dispatcherIo) {
		val file = StatisticsCachePaths.statsFile(projectDef)
		requireWithinCacheRoot(file)
		if (fileSystem.exists(file)) {
			fileSystem.delete(file)
			Logger.d("Statistics cache deleted")
		}
	}

	private fun ensureCacheDirectoryExists() {
		val cacheDir = StatisticsCachePaths.projectCacheDirectory(projectDef)
		requireWithinCacheRoot(cacheDir)
		if (!fileSystem.exists(cacheDir)) {
			fileSystem.createDirectories(cacheDir)
		}
	}

	private fun requireWithinCacheRoot(path: okio.Path) {
		val cacheRoot = StatisticsCachePaths.projectsCacheRoot()
		if (!path.isWithin(cacheRoot)) {
			error("Refusing to access statistics cache outside the cache root: $path")
		}
	}
}

/**
 * Blocking, scope-less read of the statistics cache; callers own their threading.
 * [StatisticsDatasource.loadStatistics] and the root-scoped
 * [ProjectStatisticsCacheReader] both delegate here so the format has one reader.
 */
fun readProjectStatistics(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
	toml: Toml,
): ProjectStatistics? {
	val file = StatisticsCachePaths.statsFile(projectDef)
	if (!fileSystem.exists(file)) return null
	return fileSystem.readTomlOrNull<ProjectStatistics>(file, toml) { e ->
		Logger.d("Failed to read statistics cache for ${projectDef.name}", e)
	}
}
