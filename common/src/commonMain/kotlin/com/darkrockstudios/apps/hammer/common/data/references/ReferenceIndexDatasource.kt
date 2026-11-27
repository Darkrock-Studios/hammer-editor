package com.darkrockstudios.apps.hammer.common.data.references

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

class ReferenceIndexDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val dispatcherIo by injectIoDispatcher()

	private val cacheDir by lazy { ProjectCachePaths.projectCacheDirectory(fileSystem, projectDef) }

	suspend fun loadIndex(): ReferenceIndex? = withContext(dispatcherIo) {
		val file = ProjectCachePaths.fileToRead(fileSystem, cacheDir, projectDef, FILENAME)
			?: return@withContext null
		fileSystem.readTomlOrNull<ReferenceIndex>(file, toml) { e ->
			Napier.e("Failed to load reference index cache", e)
		}
	}

	suspend fun saveIndex(index: ReferenceIndex) = withContext(dispatcherIo) {
		val file = ProjectCachePaths.fileToWrite(fileSystem, cacheDir, projectDef, FILENAME)
		fileSystem.writeToml(file, toml, index)
		Napier.d("Reference index saved to cache")
	}

	suspend fun delete() = withContext(dispatcherIo) {
		fileSystem.delete(cacheDir / FILENAME)
		Napier.d("Reference index cache deleted")
	}

	companion object {
		const val FILENAME = "references.toml"
	}
}
