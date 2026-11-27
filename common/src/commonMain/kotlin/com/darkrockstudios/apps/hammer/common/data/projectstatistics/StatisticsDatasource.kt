package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.base.http.readTomlOrNull
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.data.ProjectCachePaths
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
	private val cacheDir by lazy { ProjectCachePaths.projectCacheDirectory(fileSystem, projectDef) }

	suspend fun loadStatistics(): ProjectStatistics? = withContext(dispatcherIo) {
		readProjectStatistics(projectDef, fileSystem, toml)
	}

	suspend fun saveStatistics(stats: ProjectStatistics) = withContext(dispatcherIo) {
		val file = ProjectCachePaths.fileToWrite(fileSystem, cacheDir, projectDef, FILENAME)
		fileSystem.writeToml(file, toml, stats)
		Napier.d("Statistics saved to cache")
	}

	suspend fun delete() = withContext(dispatcherIo) {
		fileSystem.delete(cacheDir / FILENAME)
		Napier.d("Statistics cache deleted")
	}

	companion object {
		const val FILENAME = "stats.toml"
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
	val dir = ProjectCachePaths.projectCacheDirectory(fileSystem, projectDef)
	val file = ProjectCachePaths.fileToRead(fileSystem, dir, projectDef, StatisticsDatasource.FILENAME) ?: return null
	return fileSystem.readTomlOrNull<ProjectStatistics>(file, toml) { e ->
		Napier.d("Failed to read statistics cache for ${projectDef.name}", e)
	}
}
