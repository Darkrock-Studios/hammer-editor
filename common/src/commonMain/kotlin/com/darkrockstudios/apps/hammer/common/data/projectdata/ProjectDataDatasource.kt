package com.darkrockstudios.apps.hammer.common.data.projectdata

import com.darkrockstudios.apps.hammer.base.http.projectdata.ProjectData
import com.darkrockstudios.apps.hammer.base.http.readTomlOrNull
import com.darkrockstudios.apps.hammer.base.http.writeToml
import com.darkrockstudios.apps.hammer.common.data.ProjectDef
import com.darkrockstudios.apps.hammer.common.data.ProjectScoped
import com.darkrockstudios.apps.hammer.common.dependencyinjection.ProjectDefScope
import com.darkrockstudios.apps.hammer.common.dependencyinjection.injectIoDispatcher
import com.darkrockstudios.apps.hammer.common.fileio.okio.toOkioPath
import io.github.aakira.napier.Napier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import net.peanuuutz.tomlkt.Toml
import okio.FileSystem
import okio.Path

/**
 * Stored as a sibling of `project.toml` rather than merged into it because
 * `project.toml` holds local-only state (`lastAccessed`, `dataVersion`) we
 * don't sync.
 */
class ProjectDataDatasource(
	private val fileSystem: FileSystem,
	private val toml: Toml,
	val projectDef: ProjectDef,
) : ProjectScoped {

	override val projectScope = ProjectDefScope(projectDef)
	private val dispatcherIo by injectIoDispatcher()

	fun getPath(): Path = projectDef.path.toOkioPath() / FILENAME

	suspend fun load(): StoredProjectData = withContext(dispatcherIo) {
		val path = getPath()
		if (!fileSystem.exists(path)) return@withContext StoredProjectData()
		fileSystem.readTomlOrNull<StoredProjectData>(path, toml) { e ->
			Napier.e("Failed to load project_data.toml: $path", e)
		} ?: StoredProjectData()
	}

	suspend fun save(stored: StoredProjectData): Unit = withContext(dispatcherIo) {
		saveStoredProjectData(projectDef, fileSystem, toml, stored)
	}

	companion object {
		const val FILENAME = "project_data.toml"
	}
}

/** [lastSyncedHash] is replayed as `originalHash` on the next upload for conflict detection. */
@Serializable
data class StoredProjectData(
	val data: ProjectData = ProjectData(),
	val lastSyncedHash: String? = null,
)

/**
 * Reads `project_data.toml` for [projectDef] off the IO dispatcher without
 * opening a per-project Koin scope. Returns `StoredProjectData()` defaults
 * when the file is missing or unreadable so callers don't need to handle
 * I/O errors themselves.
 */
suspend fun loadStoredProjectData(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
	toml: Toml,
): StoredProjectData = withContext(Dispatchers.IO) {
	val path = projectDef.path.toOkioPath() / ProjectDataDatasource.FILENAME
	fileSystem.readTomlOrNull<StoredProjectData>(path, toml) { e ->
		Napier.d("No project_data.toml at $path (${e.message})")
	} ?: StoredProjectData()
}

/**
 * The single writer for `project_data.toml`. [ProjectDataDatasource.save] delegates here;
 * scope-less callers (project creation, before a per-project Koin scope exists) use it
 * directly so the on-disk format has exactly one owner.
 */
fun saveStoredProjectData(
	projectDef: ProjectDef,
	fileSystem: FileSystem,
	toml: Toml,
	stored: StoredProjectData,
) {
	fileSystem.writeToml(projectDef.path.toOkioPath() / ProjectDataDatasource.FILENAME, toml, stored)
}
