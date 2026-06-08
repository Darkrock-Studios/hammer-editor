package com.darkrockstudios.apps.hammer.common.data.references

import com.darkrockstudios.apps.hammer.base.http.readTomlOrNull
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

class ReferenceIndexDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val dispatcherIo by injectIoDispatcher()

	suspend fun loadIndex(): ReferenceIndex? = withContext(dispatcherIo) {
		val file = getIndexPath()
		return@withContext if (fileSystem.exists(file)) {
			fileSystem.readTomlOrNull<ReferenceIndex>(file, toml) { e ->
				Napier.e("Failed to load reference index cache", e)
			}
		} else {
			null
		}
	}

	suspend fun saveIndex(index: ReferenceIndex) = withContext(dispatcherIo) {
		ensureCacheDirectoryExists()
		val file = getIndexPath()
		fileSystem.writeToml(file, toml, index)
		Napier.d("Reference index saved to cache")
	}

	fun exists(): Boolean = fileSystem.exists(getIndexPath())

	suspend fun delete() = withContext(dispatcherIo) {
		val file = getIndexPath()
		if (fileSystem.exists(file)) {
			fileSystem.delete(file)
			Napier.d("Reference index cache deleted")
		}
	}

	private fun getProjectCacheDirectory(): Path {
		return getCacheDirectory().toPath() / PROJECTS_DIRECTORY / projectDef.name
	}

	private fun getIndexPath(): Path = getProjectCacheDirectory() / FILENAME

	private fun ensureCacheDirectoryExists() {
		val cacheDir = getProjectCacheDirectory()
		if (!fileSystem.exists(cacheDir)) {
			fileSystem.createDirectories(cacheDir)
		}
	}

	companion object {
		const val PROJECTS_DIRECTORY = "projects"
		const val FILENAME = "references.toml"
	}
}
