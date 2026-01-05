package com.darkrockstudios.apps.hammer.common.data.projectstatistics

import com.darkrockstudios.apps.hammer.base.http.readToml
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.getCacheDirectory
import io.github.aakira.napier.Napier
import kotlinx.coroutines.withContext
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

class StatisticsDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val dispatcherIo by injectIoDispatcher()

	suspend fun loadStatistics(): ProjectStatistics? = withContext(dispatcherIo) {
		val file = getStatisticsPath()
		return@withContext if (fileSystem.exists(file)) {
			try {
				fileSystem.readToml(file, toml)
			} catch (e: Exception) {
				Napier.e("Failed to load statistics cache", e)
				null
			}
		} else {
			null
		}
	}

	suspend fun saveStatistics(stats: ProjectStatistics) = withContext(dispatcherIo) {
		ensureCacheDirectoryExists()
		val file = getStatisticsPath()
		fileSystem.writeToml(file, toml, stats)
		Napier.d("Statistics saved to cache")
	}

	fun exists(): Boolean {
		return fileSystem.exists(getStatisticsPath())
	}

	suspend fun delete() = withContext(dispatcherIo) {
		val file = getStatisticsPath()
		if (fileSystem.exists(file)) {
			fileSystem.delete(file)
			Napier.d("Statistics cache deleted")
		}
	}

	private fun getProjectCacheDirectory(): Path {
		return getCacheDirectory().toPath() / PROJECTS_DIRECTORY / projectDef.name
	}

	private fun getStatisticsPath(): Path {
		return getProjectCacheDirectory() / FILENAME
	}

	private fun ensureCacheDirectoryExists() {
		val cacheDir = getProjectCacheDirectory()
		if (!fileSystem.exists(cacheDir)) {
			fileSystem.createDirectories(cacheDir)
		}
	}

	companion object {
		const val PROJECTS_DIRECTORY = "projects"
		const val FILENAME = "stats.toml"
	}
}
